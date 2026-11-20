"""The executable request/response scenario and the operations it exercises.

Starting point: an HTTP scenario file (``sources.e2e_scenario``) mapped to the operations the
application registers. ``examples.operation_chapters`` in ``docs/scripts/config.json`` names the
chapter slug per operation; a new operation without an entry fails extraction instead of
shipping an uncomposed document.
"""

from __future__ import annotations

import json
import re

from . import common, openapi, security

METHOD_LINE = re.compile(r"^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s+\S+")
BLOCK_MARKERS = ("[Asserts]", "[Captures]", "[FormParams]", "[QueryStringParams]")

# The chapter slug per operation is part of the contract with the manual segments that include the
# chapters. It lives in ``examples.operation_chapters`` in docs/scripts/config.json; a new endpoint
# without an entry fails the extraction instead of shipping an uncomposed document.
CHAPTERS = {
    tuple(key.split(" ", 1)): slug
    for key, slug in common.config.DATA["examples"]["operation_chapters"].items()
}


def _pairs(lines: list[str]) -> list[dict]:
    pairs = []
    for line in lines:
        name, separator, value = line.partition(":")
        if separator:
            pairs.append({"name": name.strip(), "value": value.strip()})
    return pairs


def _section(lines: list[str], marker: str) -> list[str]:
    if marker not in lines:
        return []
    start = lines.index(marker) + 1
    collected: list[str] = []
    for line in lines[start:]:
        if line in BLOCK_MARKERS or re.fullmatch(r"HTTP \d{3}", line):
            break
        if line:
            collected.append(line)
    return collected


def blocks() -> list[dict]:
    lines = common.read(common.E2E_SCENARIO).splitlines()
    entries: list[dict] = []
    index = 0
    while index < len(lines):
        if not METHOD_LINE.match(lines[index]):
            index += 1
            continue
        start = index
        index += 1
        while index < len(lines) and not METHOD_LINE.match(lines[index]):
            index += 1
        end = index
        while end > start and (not lines[end - 1].strip() or lines[end - 1].lstrip().startswith("#")):
            end -= 1
        entries.append(_block(len(entries) + 1, start + 1, end, lines[start:end]))
    return entries


def _block(number: int, start: int, end: int, lines: list[str]) -> dict:
    if not lines:
        raise SystemExit(f"{common.E2E_SCENARIO}:{start} has no request line")
    method, _, target = lines[0].partition(" ")
    target = target.strip()
    url_path, _, query = target.partition("?")
    headers_at = 1
    while headers_at < len(lines) and re.match(r"^[A-Za-z-]+:", lines[headers_at]):
        headers_at += 1
    body_start = next(
        (position for position, line in enumerate(lines) if line.lstrip().startswith(("{", "["))),
        None,
    )
    status_at = next(
        (position for position, line in enumerate(lines) if re.fullmatch(r"HTTP \d{3}", line.strip())),
        len(lines),
    )
    body_raw = None
    if body_start is not None and body_start < status_at:
        body_raw = "\n".join(lines[body_start:status_at]).rstrip()
    body = None
    if body_raw:
        try:
            body = json.loads(body_raw)
        except json.JSONDecodeError:
            body = None
    statuses = [line.strip().partition(" ")[2] for line in lines
                if re.fullmatch(r"HTTP \d{3}", line.strip())]
    return {
        "index": number,
        "start": start,
        "end": end,
        "raw": "\n".join(lines),
        "method": method,
        "target": target,
        "path": re.sub(r"^\{\{[^}]*\}\}", "", url_path),
        "query": query or None,
        "headers": _pairs(lines[1:headers_at]),
        "form_params": _pairs(_section(lines, "[FormParams]")),
        "body": body,
        "body_raw": body_raw,
        "expected_status": statuses[0] if statuses else None,
        "assertions": _section(lines, "[Asserts]"),
        "captures": _pairs(_section(lines, "[Captures]")),
        "evidence": [common.ref(common.E2E_SCENARIO, start, end)],
    }


def path_matches(declared: str, actual: str) -> bool:
    """Match a declared route template against a concrete request path, ignoring case and separators."""
    left = declared.strip("/").split("/")
    right = actual.strip("/").split("/")
    if len(left) != len(right):
        return False
    for declared_part, actual_part in zip(left, right):
        if declared_part.startswith("{") and declared_part.endswith("}"):
            continue
        if common.normalise_segment(declared_part) != common.normalise_segment(actual_part):
            return False
    return True


def path_matches_template(declared: str, actual: str) -> bool:
    """Exact, case-sensitive match: a route template admits any value for its variables, nothing else."""
    left = declared.strip("/").split("/")
    right = actual.strip("/").split("/")
    if len(left) != len(right):
        return False
    for declared_part, actual_part in zip(left, right):
        if declared_part.startswith("{") and declared_part.endswith("}"):
            continue
        if declared_part != actual_part:
            return False
    return True


def _openapi_operation_ids() -> dict[tuple[str, str], str]:
    """The identifier the contract dataset gives each route, keyed by ``(method, path)``."""
    return openapi.operation_ids()


def operations() -> list[dict]:
    identifiers = _openapi_operation_ids()
    decisions = security.extract()
    merged: dict[tuple[str, str], dict] = {}
    for declared in common.controller_operations():
        key = (declared["method"], declared["path"])
        if key not in CHAPTERS:
            raise SystemExit(f"no chapter name for {declared['method']} {declared['path']}; "
                             f"add it to examples.operation_chapters in docs/scripts/config.json")
        access = _access(declared["method"], declared["path"], decisions)
        operation = merged.setdefault(key, {
            "method": declared["method"],
            "path": declared["path"],
            "chapter": CHAPTERS[key],
            "operation_id": identifiers.get(key),
            "consumes": [],
            "produces": [],
            "path_params": [],
            "query_params": [],
            "request_types": [],
            "handlers": [],
            "access": access["decision"],
            "access_scope": access["scope"],
            "access_evidence": access["evidence"],
            "sources": [],
            "evidence": list(access["evidence"]),
        })
        for field, values in (("consumes", declared["consumes"]), ("produces", declared["produces"]),
                              ("path_params", declared["path_params"]),
                              ("query_params", declared["mapping_params"] + declared["query_params"])):
            for value in values:
                if value and value not in operation[field]:
                    operation[field].append(value)
        if declared["request_type"] and declared["request_type"] not in operation["request_types"]:
            operation["request_types"].append(declared["request_type"])
        if declared["handler"] not in operation["handlers"]:
            operation["handlers"].append({"name": declared["handler"], "return_type": declared["return_type"]})
        operation["sources"].append(declared["source"])
        for reference in (common.ref(declared["source"]["path"], declared["source"]["line"]),
                          common.ref(declared["source"]["path"], declared["source"]["signature_line"])):
            if reference not in operation["evidence"]:
                operation["evidence"].append(reference)
    return list(merged.values())


def _access(method: str, path: str, decisions: dict) -> dict:
    """The decision that governs this request, with the line of the rule that declares it."""
    chain = decisions["filter_chain"]
    for rule in chain["rules"]:
        if rule["method"] in (None, method) and _spring_pattern_matches(rule["matcher"], path):
            return {"decision": _category(rule["decision"]),
                    "scope": rule["authorities"],
                    "evidence": [common.ref(chain["path"], rule["line"])]}
    return {"decision": _category(chain["fallback"]["decision"]),
            "scope": [],
            "evidence": [common.ref(chain["path"], chain["fallback"]["line"])]}


def _category(decision: str) -> str:
    """Whether the decision admits an anonymous caller: only ``permitAll`` does."""
    return "permitAll" if decision == "permitAll" else "authenticated"


def _spring_pattern_matches(pattern: str, path: str) -> bool:
    """A path pattern with ``{variable}`` segments and ``*``/``**`` wildcards, as Spring reads it."""
    segments = re.escape(pattern).replace(r"\*\*", ".*").replace(r"\*", "[^/]*")
    segments = re.sub(r"\\\{[^/]*?\\\}", "[^/]+", segments)
    return re.match("^" + segments + "$", path) is not None


def extract() -> dict:
    found = blocks()
    declared = operations()
    for block in found:
        match = next(
            (operation for operation in declared
             if operation["method"] == block["method"] and path_matches(operation["path"], block["path"])),
            None,
        )
        block["operation"] = (
            {"method": match["method"], "path": match["path"], "chapter": match["chapter"],
             "operation_id": match["operation_id"]}
            if match else None
        )
        block["path_matches_template"] = (
            path_matches_template(match["path"], block["path"]) if match else None
        )
    return {"scenario": common.E2E_SCENARIO, "examples": found, "operations": declared}
