"""The reader for the hurl suite that stands in for a missing OpenAPI document.

A hurl entry is a request line (``METHOD url``), the headers and the body the request sends, the
status it expects (``HTTP nnn``) and the ``[Captures]``/``[Asserts]`` sections that follow. Both
suites in this repository assert their responses through jsonpath expressions and carry no expected
response body, so the response side of an entry is exactly those sections.

Nothing here guesses. A ``{{placeholder}}`` is reported with the line that declares it, or listed as
unresolved; a request body that is not valid JSON is recorded verbatim and left unparsed; a path
segment that matches no controller route is recorded as unmatched. Every record carries the
``path:line`` of the line it was read from.
"""

from __future__ import annotations

import json
import re

from . import common

METHODS = ("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
REQUEST = re.compile(r"^(%s)\s+(\S+)\s*$" % "|".join(METHODS))
HEADER = re.compile(r"^([A-Za-z0-9][A-Za-z0-9!#$%&'*+.^_`|~-]*):\s*(.*)$")
STATUS = re.compile(r"^HTTP(?:\s+\S+)?\s+(\d{3})\s*$")
SECTION = re.compile(r"^\[([A-Za-z]+)\]\s*$")
PLACEHOLDER = re.compile(r"\{\{\s*([\w.-]+)\s*\}\}")
DECLARED = re.compile(r"--variable\s+([\w.-]+)=(\S+)")
CAPTURE = re.compile(r"^([\w.-]+)\s*:\s*(jsonpath|header|xpath|regex|status|body|url|cookie)\s+(.+)$")
ASSERT = re.compile(
    r'^(jsonpath|header|body|status|url|cookie)\s+(?:"([^"]*)"|(\S+))?\s*(.*)$'
)
ROUTE_PARAMETER = re.compile(r"^\{(\w+)\}$")
LITERAL_INTEGER = re.compile(r"^-?\d+$")


def _brace_delta(line: str) -> int:
    """How many brackets a line opens, ignoring the ones inside a JSON string."""
    depth, quoted, escaped = 0, False, False
    for character in line:
        if quoted:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == '"':
                quoted = False
        elif character == '"':
            quoted = True
        elif character in "{[(":
            depth += 1
        elif character in "}])":
            depth -= 1
    return depth


def _body(lines: list[str], index: int) -> tuple[dict, int]:
    """The body that starts at ``index``: a brace-balanced JSON block or a backtick-fenced one."""
    first = lines[index]
    start = index + 1
    if first.lstrip().startswith("`"):
        if first.count("`") >= 2:
            content = first[first.index("`") + 1: first.rindex("`")]
            return {"kind": "fenced", "raw": content, "start": start, "end": start}, index + 1
        collected = []
        index += 1
        while index < len(lines):
            collected.append(lines[index])
            index += 1
            if "`" in collected[-1]:
                break
        return {"kind": "fenced", "raw": "\n".join(collected), "start": start,
                "end": index}, index
    depth, collected = 0, []
    while index < len(lines):
        collected.append(lines[index])
        depth += _brace_delta(lines[index])
        index += 1
        if depth <= 0:
            break
    return {"kind": "json", "raw": "\n".join(collected), "start": start, "end": index}, index


def _capture(raw: str, line: int, source: str) -> dict:
    match = CAPTURE.match(raw)
    if not match:
        return {"unparsed": raw, "line": line, "source": f"{source}:{line}"}
    return {"name": match.group(1), "target": match.group(2),
            "expression": match.group(3).strip().strip('"'),
            "raw": raw.strip(), "line": line, "source": f"{source}:{line}"}


def _assert(raw: str, line: int, source: str) -> dict:
    """One ``[Asserts]`` line: what it targets, the path it names, and the predicate after it.

    Only ``jsonpath`` and ``header`` carry a quoted operand (``"$.x"``, ``"Content-Type"``);
    ``body``, ``status``, ``url`` and ``cookie`` start straight at the predicate, so ``body == ""``
    must not read the operator as its expression.
    """
    match = ASSERT.match(raw)
    if not match:
        return {"unparsed": raw, "line": line, "source": f"{source}:{line}"}
    expression = match.group(2)
    if expression is None and match.group(3) is not None:
        predicate = " ".join(part for part in (match.group(3), match.group(4)) if part).strip()
    else:
        predicate = (match.group(4) or "").strip()
    negated = predicate.startswith("not ")
    if negated:
        predicate = predicate[4:].strip()
    if predicate.startswith("count "):
        kind, predicate = "count", predicate[6:].strip()
    else:
        kind = "predicate"
    operator, _, value = predicate.partition(" ")
    return {
        "target": match.group(1),
        "expression": expression.strip().strip('"') if expression is not None else None,
        "kind": kind,
        "operator": operator.strip(),
        "value": value.strip(),
        "negated": negated,
        "raw": raw.strip(),
        "line": line,
        "source": f"{source}:{line}",
    }


def _variables(lines: list[str], source: str) -> list[dict]:
    """Every ``--variable name=value`` the run command in the header declares, with its line."""
    found: list[dict] = []
    seen = set()
    for number, line in enumerate(lines, start=1):
        if not line.lstrip().startswith("#"):
            continue
        for name, value in DECLARED.findall(line):
            if name in seen:
                continue
            seen.add(name)
            found.append({"name": name, "declared": value, "line": number,
                          "source": f"{source}:{number}"})
    return found


def placeholders(text: str) -> list[str]:
    return sorted(set(PLACEHOLDER.findall(text)))


def split_url(url: str) -> tuple[str | None, str, list[str]]:
    """The base URL a request names, its path and its raw query pairs."""
    base, rest = None, url
    match = re.match(r"^\{\{\s*([\w.-]+)\s*\}\}(.*)$", url)
    if match:
        base, rest = match.group(1), match.group(2)
    else:
        literal = re.match(r"^[a-z]+://[^/]+(.*)$", url)
        if literal:
            base, rest = url[: len(url) - len(literal.group(1))], literal.group(1)
    query = ""
    if "?" in rest:
        rest, _, query = rest.partition("?")
    return base, rest or "/", [pair for pair in query.split("&") if pair]


def segments(path: str) -> list[str]:
    return [part for part in path.strip("/").split("/") if part]


def request_path(path: str) -> tuple[str, list[dict]]:
    """A path with each ``{{placeholder}}`` replaced by ``{name}``, and the placeholders it held."""
    bindings: list[dict] = []
    parts: list[str] = []
    for part in segments(path):
        names = PLACEHOLDER.findall(part)
        if len(names) == 1 and part.strip() == "{{" + names[0] + "}}":
            bindings.append({"segment": names[0], "kind": "placeholder"})
            parts.append("{" + names[0] + "}")
            continue
        if names:
            bindings.append({"segment": names[0], "kind": "embedded"})
        parts.append(part)
    return "/" + "/".join(parts), bindings


def query_parameters(pairs: list[str], line: int, source: str) -> list[dict]:
    parameters = []
    for pair in pairs:
        name, _, value = pair.partition("=")
        literal = None
        if PLACEHOLDER.search(value) or not value:
            kind = "unresolved" if not value else "placeholder"
        elif LITERAL_INTEGER.match(value):
            literal, kind = int(value), "integer"
        else:
            literal, kind = value, "string"
        parameters.append({"name": name, "observed": value, "literal": literal, "kind": kind,
                           "line": line, "source": f"{source}:{line}"})
    return parameters


def entry_index(rel_path: str) -> list[dict]:
    """Every request the suite sends, in file order, with the sections that follow it."""
    lines = common.read(rel_path).splitlines()
    entries: list[dict] = []
    index = 0
    while index < len(lines):
        match = REQUEST.match(lines[index])
        if not match:
            index += 1
            continue
        start = index
        method, url = match.group(1), match.group(2)
        index += 1
        headers: list[dict] = []
        body: dict | None = None
        while index < len(lines):
            current = lines[index]
            if STATUS.match(current) or SECTION.match(current) or REQUEST.match(current):
                break
            if not current.strip() or current.lstrip().startswith("#"):
                index += 1
                continue
            if body is None and current.lstrip().startswith(("{", "[", "`")):
                body, index = _body(lines, index)
                continue
            header = HEADER.match(current)
            if header and body is None:
                headers.append({"name": header.group(1), "value": header.group(2).strip(),
                                "line": index + 1, "source": f"{rel_path}:{index + 1}"})
                index += 1
                continue
            body, index = _body(lines, index)
        request_end = index
        status, status_line = None, None
        if index < len(lines) and STATUS.match(lines[index]):
            status, status_line = STATUS.match(lines[index]).group(1), index + 1
            index += 1
        section, captures, asserts, unparsed = None, [], [], []
        while index < len(lines) and not REQUEST.match(lines[index]):
            current = lines[index]
            header = SECTION.match(current)
            if header:
                section = header.group(1)
                index += 1
                continue
            if not current.strip() or current.lstrip().startswith("#"):
                index += 1
                continue
            if section == "Captures":
                captures.append(_capture(current.strip(), index + 1, rel_path))
            elif section == "Asserts":
                asserts.append(_assert(current.strip(), index + 1, rel_path))
            else:
                unparsed.append({"section": section, "raw": current.strip(), "line": index + 1,
                                 "source": f"{rel_path}:{index + 1}"})
            index += 1
        base, path, query = split_url(url)
        template, bindings = request_path(path)
        entries.append({
            "index": len(entries) + 1,
            "method": method,
            "url": url,
            "base": base,
            "path": path,
            "query": query,
            "template": template,
            "bindings": bindings,
            "query_parameters": query_parameters(query, start + 1, rel_path),
            "headers": headers,
            "body": body,
            "expected_status": status,
            "status_line": f"{rel_path}:{status_line}" if status_line else None,
            "captures": captures,
            "asserts": asserts,
            "unparsed": unparsed,
            "raw": "\n".join(lines[start:request_end]),
            "line": start + 1,
            "line_end": request_end,
            "source": f"{rel_path}:{start + 1}",
            "evidence": [common.ref(rel_path, start + 1, request_end)]
            + ([common.ref(rel_path, status_line)] if status_line else []),
        })
    return entries


def suite(rel_path: str) -> dict:
    """The requests of one suite and the variables it reads, declared or unresolved."""
    lines = common.read(rel_path).splitlines()
    entries = entry_index(rel_path)
    captured = {capture.get("name"): capture["source"]
                for entry in entries for capture in entry["captures"] if "name" in capture}
    declared = _variables(lines, rel_path)
    kinds = {item["name"]: item for item in declared}
    references: dict[str, dict] = {}
    for entry in entries:
        texts = [entry["url"]] + [header["value"] for header in entry["headers"]]
        if entry["body"]:
            texts.append(entry["body"]["raw"])
        for name in (name for text in texts for name in placeholders(text)):
            if name in references:
                references[name]["lines"].append(entry["line"])
                continue
            if name == "BASE_URL":
                kind = "base_url"
            elif name in captured:
                kind = "captured"
            elif name in kinds:
                kind = "harness"
            else:
                kind = "unresolved"
            references[name] = {
                "name": name,
                "kind": kind,
                "declared": kinds[name]["declared"] if name in kinds else None,
                "declaration": (kinds[name]["source"] if name in kinds
                                else captured.get(name)),
                "lines": [entry["line"]],
            }
    variables = []
    for name in sorted(references):
        item = references[name]
        variables.append({
            "name": name,
            "kind": item["kind"],
            "declared": item["declared"],
            "declaration": item["declaration"],
            "first_request": f"{rel_path}:{item['lines'][0]}",
            "requests": len(item["lines"]),
        })
    unresolved = [item["name"] for item in variables if item["kind"] == "unresolved"]
    return {
        "path": rel_path,
        "lines": len(lines),
        "requests": entries,
        "variables": variables,
        "unresolved_placeholders": unresolved,
        "unparsed": [item for entry in entries for item in entry["unparsed"]],
    }


def literal(text: object):
    """A hurl assertion value as JSON reads it, or the raw token when it is not a JSON literal."""
    if not isinstance(text, str) or not text.strip():
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return text


def jsonpath_parts(expression: str | None) -> list[tuple[str, str]]:
    """``$.items[0].state`` as ``[("property", "items"), ("index", "0"), ("property", "state")]``."""
    if not expression:
        return []
    rest = expression[1:] if expression.startswith("$") else expression
    parts: list[tuple[str, str]] = []
    for match in re.finditer(r"\.([A-Za-z_][\w-]*)|\[(\*|\d+)\]", rest):
        if match.group(1) is not None:
            parts.append(("property", match.group(1)))
        else:
            parts.append(("index", match.group(2)))
    return parts
