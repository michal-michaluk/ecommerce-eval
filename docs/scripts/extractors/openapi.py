"""The service contract, synthesised from what this repository actually declares.

The service ships no ``springdoc`` artifact and no OpenAPI document, so the contract dataset is read
from the two places the repository does state it:

* the routes — every request mapping of every ``@RestController`` under ``sources.main_root``, with
  the command record it binds, the record it answers and the line that declares it;
* the executable contract — ``sources.e2e_scenario``, whose request lines, bodies, expected statuses
  and ``[Captures]``/``[Asserts]`` sections are what the suite proves about each route.

A field therefore appears because an assertion names it and a status because an entry expects it;
nothing is inferred from a document that does not exist. A route the suite never calls is recorded as
``exercised: false`` with no request, response or field, so a gap in the contract stays visible
instead of being filled in.

The title of an operation is the Javadoc that documents it: the handler's own first sentence, or —
because this service documents no handler — the first sentence of the class Javadoc when that class
maps one route and therefore documents that one operation. A class that maps several routes
documents the class and none of them in particular, so each of its operations is titled by its
handler name instead and ``summary_source`` says so. ``description`` is the Javadoc's remaining
sentences and stays empty when it has none.
"""

from __future__ import annotations

import json
import re

from . import common, hurl

HTTP_METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}
JSON_MEDIA = "application/json"
ROUTE_PARAMETER = hurl.ROUTE_PARAMETER
HANDLER_WORD = re.compile(r"(?<=[a-z0-9])(?=[A-Z])")


def humanise(name: str) -> str:
    """A handler identifier read as words: ``reportIssue`` -> ``Report issue``."""
    words = HANDLER_WORD.sub(" ", name).split()
    if not words:
        return name
    return " ".join([words[0].capitalize(), *(word.lower() for word in words[1:])])


def title(declaration: dict, routes_in_class: int) -> dict:
    """The title of one operation and the Javadoc line each derived string came from."""
    source = declaration["source"]
    document = common.javadoc(source["path"], source["line"])
    if document is None and routes_in_class == 1:
        document = common.javadoc(source["path"], source["class_line"])
    if document is None:
        return {
            "summary": humanise(declaration["handler"]),
            "description": "",
            "summary_source": "handler",
            "summary_evidence": [common.ref(source["path"], source["signature_line"])],
            "description_evidence": [],
        }
    return {
        "summary": document["summary"],
        "description": document["description"],
        "summary_source": "javadoc",
        "summary_evidence": [common.ref(source["path"], document["summary_line"])],
        "description_evidence": ([common.ref(source["path"], document["description_line"])]
                                 if document["description"] else []),
    }


def _require_titles(found: list[dict]) -> None:
    """Every operation carries a title of its own, so no chapter ships an empty or shared heading."""
    seen: dict[str, str] = {}
    for route in found:
        where = f"{route['method']} {route['path']}"
        if not route["summary"].strip():
            raise SystemExit(f"{route['source']}: {where} has no summary; document the route or "
                             f"name its handler so the chapter can be titled")
        if route["summary"] in seen:
            raise SystemExit(f"{route['source']}: {where} and {seen[route['summary']]} would share "
                             f"the summary {route['summary']!r}; a chapter title names its operation")
        seen[route["summary"]] = where


def _media(names: list[str]) -> list[str]:
    """The declared media types whose annotation resolved to one this repository can name."""
    return sorted({name for name in names if name and "/" in name})


def routes() -> list[dict]:
    """Every mapped operation of the service, with the line that declares it."""
    declared = [operation for operation in common.controller_operations()
                if operation["method"] in HTTP_METHODS]
    found: list[dict] = []
    for operation in declared:
        source = operation["source"]
        in_class = sum(1 for other in declared if other["source"]["path"] == source["path"]
                       and other["source"]["class_line"] == source["class_line"])
        found.append({
            "method": operation["method"],
            "path": operation["path"],
            "handler": {"name": operation["handler"],
                        "return_type": operation["return_type"],
                        "source": common.ref(source["path"], source["signature_line"])},
            "request_type": operation["request_type"],
            "declared": {"consumes": _media(operation["consumes"]),
                         "produces": _media(operation["produces"])},
            "path_params": [part[1:-1] for part in hurl.segments(operation["path"])
                            if part.startswith("{") and part.endswith("}")],
            "evidence": [common.ref(source["path"], source["line"])],
            "source": common.ref(source["path"], source["line"]),
            **title(operation, in_class),
        })
    _require_titles(found)
    return found


def match(index: list[dict], method: str, path: str) -> tuple[dict | None, dict]:
    """The route a request addresses, preferring the one with the fewest wildcard segments."""
    wanted = hurl.segments(path)
    candidates: list[tuple[int, str, dict, dict]] = []
    for route in index:
        if route["method"] != method:
            continue
        parts = hurl.segments(route["path"])
        if len(parts) != len(wanted):
            continue
        bindings, score, matched = {}, 0, True
        for declared, actual in zip(parts, wanted):
            parameter = ROUTE_PARAMETER.match(declared)
            if parameter:
                score += 1
                bindings[parameter.group(1)] = actual
            elif declared != actual:
                matched = False
                break
        if matched:
            candidates.append((score, route["path"], route, bindings))
    if not candidates:
        return None, {}
    _, _, route, bindings = min(candidates, key=lambda item: (item[0], item[1]))
    return route, bindings


def json_type(value: object) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, int):
        return "integer"
    if isinstance(value, float):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    return "object"


def _key_lines(raw: str, start: int) -> dict[str, int]:
    """The first line each ``"name":`` pair of a JSON body occupies, for field provenance."""
    found: dict[str, int] = {}
    for offset, line in enumerate(raw.splitlines()):
        for name in re.findall(r'"([A-Za-z_][\w.-]*)"\s*:', line):
            found.setdefault(name, start + offset)
    return found


def schema_of(values: list, lines: dict[str, int], path: str) -> dict:
    """A JSON schema for the values one field was observed with, each property carrying its line."""
    kinds = {json_type(value) for value in values}
    named = sorted(kinds - {"null"})
    objects = [value for value in values if isinstance(value, dict)]
    arrays = [value for value in values if isinstance(value, list)]
    schema: dict = {}
    if len(named) == 1:
        schema["type"] = named[0]
    elif len(named) > 1:
        schema["observed_types"] = named
    if "null" in kinds:
        schema["nullable"] = True
    if objects:
        properties: dict[str, dict] = {}
        required: list[str] = []
        for name in sorted({key for value in objects for key in value}):
            present = [value[name] for value in objects if name in value]
            properties[name] = schema_of(present, lines, path)
            if len(present) == len(objects):
                required.append(name)
            if name in lines:
                properties[name]["evidence"] = [f"{path}:{lines[name]}"]
        schema["properties"] = properties
        if required:
            schema["required"] = required
    if arrays:
        elements = [element for value in arrays for element in value]
        if elements:
            schema["items"] = schema_of(elements, lines, path)
    return schema


def _request_body(entries: list[dict], path: str) -> dict | None:
    """The request bodies the suite sends to one operation, merged into one declared schema.

    A body that is not valid JSON is recorded with the line it starts on instead of being read: the
    suite sends one such body on purpose, to prove the service refuses unreadable JSON.
    """
    parsed, evidence, unparsed, lines = [], [], [], {}
    for entry in entries:
        body = entry["body"]
        if not body:
            continue
        try:
            value = json.loads(body["raw"])
        except json.JSONDecodeError:
            unparsed.append({"kind": body["kind"], "raw": body["raw"],
                             "source": f"{path}:{body['start']}"})
            continue
        parsed.append((value, body))
        evidence.append(f"{path}:{body['start']}")
        for name, line in _key_lines(body["raw"], body["start"]).items():
            lines.setdefault(name, line)
    if not parsed:
        return {"content": {}, "unparsed_bodies": unparsed} if unparsed else None
    schema = schema_of([value for value, _ in parsed], lines, path)
    media_types = sorted({header["value"] for entry in entries for header in entry["headers"]
                          if header["name"].lower() == "content-type" and header["value"]})
    declared = {
        "required": True,
        "content": {media_type: {"schema": schema} for media_type in media_types or [JSON_MEDIA]},
        "observed_bodies": len({body["raw"] for _, body in parsed}),
        "evidence": evidence,
    }
    if unparsed:
        declared["unparsed_bodies"] = unparsed
    return declared


def _node() -> dict:
    return {"properties": {}, "items": None, "types": [], "evidence": []}


def _assert_type(assertion: dict) -> str | None:
    if assertion["negated"]:
        return None
    if assertion["kind"] == "count":
        return "array"
    operator = assertion["operator"]
    if operator in {"isString", "matches", "startsWith", "endsWith"}:
        return "string"
    if operator == "isInteger":
        return "integer"
    if operator == "isFloat":
        return "number"
    if operator == "isBoolean":
        return "boolean"
    if operator in {"isCollection", "isList"}:
        return "array"
    if operator == "isObject":
        return "object"
    if operator in {"==", "!="}:
        observed = json_type(hurl.literal(assertion["value"]))
        return None if observed == "null" else observed
    return None


def _assert_tree(assertions: list[dict]) -> dict:
    """The field tree the jsonpath assertions of one status describe, built from the paths alone."""
    root = _node()
    for assertion in assertions:
        if assertion["target"] != "jsonpath":
            continue
        node = root
        parts = hurl.jsonpath_parts(assertion["expression"])
        for kind, name in parts:
            if kind == "property":
                node = node["properties"].setdefault(name, _node())
            else:
                node["items"] = node["items"] or _node()
                node = node["items"]
        node["evidence"].append(assertion["source"])
        observed = _assert_type(assertion)
        if observed and observed not in node["types"]:
            node["types"].append(observed)
    return root


def _node_schema(node: dict) -> dict:
    schema: dict = {}
    if node["items"] is not None:
        schema["type"] = "array"
        item = _node_schema(node["items"])
        if item:
            schema["items"] = item
    elif node["properties"]:
        schema["type"] = "object"
        schema["properties"] = {name: _node_schema(child)
                                for name, child in node["properties"].items()}
    elif node["types"]:
        schema["type"] = node["types"][0]
    if node["evidence"]:
        schema["evidence"] = sorted(set(node["evidence"]))
    return schema


def _response_content(assertions: list[dict]) -> dict:
    """The media types the suite proves for a status: a jsonpath proves JSON, a header names its own."""
    content: dict = {}
    if any(assertion["target"] == "jsonpath" for assertion in assertions):
        content[JSON_MEDIA] = {"schema": _node_schema(_assert_tree(assertions))}
    for assertion in assertions:
        if assertion["target"] == "header" and assertion["expression"].lower() == "content-type":
            media = str(hurl.literal(assertion["value"]) or "")
            if "/" in media:
                content.setdefault(media, {"schema": {"type": "string", "format": "binary",
                                                     "evidence": [assertion["source"]]}})
    return content


def _responses(entries: list[dict]) -> dict:
    grouped: dict[str, list[dict]] = {}
    declared_at: dict[str, list[str]] = {}
    for entry in entries:
        if entry["expected_status"] is None:
            continue
        grouped.setdefault(entry["expected_status"], []).extend(entry["asserts"])
        if entry["status_line"]:
            declared_at.setdefault(entry["expected_status"], []).append(entry["status_line"])
    responses: dict[str, dict] = {}
    for status in sorted(grouped):
        assertions = grouped[status]
        seen, listed = set(), []
        for assertion in assertions:
            if assertion["raw"] in seen:
                continue
            seen.add(assertion["raw"])
            listed.append({key: assertion[key] for key in
                           ("target", "expression", "kind", "operator", "value", "negated", "raw",
                            "source")})
        responses[status] = {
            "description": "",
            "content": _response_content(assertions),
            "assertions": listed,
            "evidence": sorted({assertion["source"] for assertion in assertions}
                               | set(declared_at.get(status, []))),
        }
    return responses


def _parameters(route: dict, bindings: list[dict], entries: list[dict]) -> list[dict]:
    rows: list[dict] = []
    for name in route["path_params"]:
        observed = sorted({binding["path_params"][name] for binding in bindings
                           if name in binding["path_params"]})
        rows.append({
            "name": name,
            "in": "path",
            "required": True,
            "schema": {"type": "string"},
            "observed": observed,
            "evidence": [route["source"]] + sorted({entry["source"] for entry in entries}),
        })
    query: dict[str, list[dict]] = {}
    for entry in entries:
        for parameter in entry["query_parameters"]:
            query.setdefault(parameter["name"], []).append(parameter)
    for name in sorted(query):
        found = query[name]
        literals = [item["literal"] for item in found if item["literal"] is not None]
        kinds = {json_type(value) for value in literals}
        schema = {"type": next(iter(kinds))} if len(kinds) == 1 else {}
        rows.append({
            "name": name,
            "in": "query",
            "required": False,
            "schema": schema,
            "observed": sorted({item["observed"] for item in found}),
            "evidence": sorted({item["source"] for item in found}),
        })
    return rows


def slug(method: str, path: str) -> str:
    parts = [part.strip("{}").lower() for part in hurl.segments(path)]
    return "-".join([method.lower()] + parts)


def operation_ids() -> dict[tuple[str, str], str]:
    """The identifier of every operation, keyed by ``(method, path)`` — the artefact's own naming rule."""
    return {(route["method"], route["path"]): slug(route["method"], route["path"])
            for route in routes()}


def operations() -> dict:
    """Every mapped operation of the service, merged with the requests that exercise it."""
    index = routes()
    suite = hurl.suite(common.E2E_SCENARIO)
    for entry in suite["requests"]:
        route, bindings = match(index, entry["method"], entry["path"])
        entry["route"] = route
        entry["path_params"] = dict(bindings)

    paths: dict[str, dict] = {}
    owners: dict[tuple[str, str], str] = {}
    not_exercised: list[dict] = []
    for route in index:
        entries = [entry for entry in suite["requests"] if entry["route"] is route]
        key = (route["path"], route["method"].lower())
        if key in owners:
            raise SystemExit(f"{route['source']}: {route['method']} {route['path']} is declared "
                             f"twice; the artefact keys operations by path and method only")
        owner = slug(route["method"], route["path"])
        owners[key] = owner
        bindings = [{"path_params": entry["path_params"]} for entry in entries]
        operation = {
            "operationId": owner,
            "method": route["method"],
            "path": route["path"],
            "summary": route["summary"],
            "description": route["description"],
            "summary_source": route["summary_source"],
            "summary_evidence": route["summary_evidence"],
            "description_evidence": route["description_evidence"],
            "handler": route["handler"],
            "request_type": route["request_type"],
            "declared": route["declared"],
            "exercised": bool(entries),
            "not_exercised": not entries,
            "parameters": _parameters(route, bindings, entries),
            "responses": _responses(entries),
            "captures": [{field: capture[field] for field in ("name", "target", "expression", "source")}
                         for entry in entries for capture in entry["captures"]],
            "requests": [{"source": entry["source"], "status": entry["expected_status"],
                          "body": bool(entry["body"]), "line": entry["line"]}
                         for entry in entries],
            "request_count": len(entries),
            "evidence": [route["source"]] + sorted({entry["source"] for entry in entries}),
            "source": route["source"],
        }
        body = _request_body(entries, common.E2E_SCENARIO) if entries else None
        if body:
            operation["requestBody"] = body
        paths.setdefault(route["path"], {})[route["method"].lower()] = operation
        if not entries:
            not_exercised.append({"method": route["method"], "path": route["path"],
                                  "evidence": [route["source"]]})

    base_url = next((variable for variable in suite["variables"] if variable["kind"] == "base_url"), None)
    return {
        "openapi": None,
        "info": {},
        "servers": [],
        "tags": [],
        "paths": paths,
        "components": {"schemas": {}, "securitySchemes": {}},
        "contract": {
            "path": suite["path"],
            "lines": suite["lines"],
            "requests": len(suite["requests"]),
            "operations": len(owners),
            "exercised_operations": sum(1 for path in paths.values() for operation in path.values()
                                        if operation["exercised"]),
            "base_url": base_url,
            "variables": suite["variables"],
            "evidence": [entry["source"] for entry in suite["requests"]][:1]
            or [common.ref(suite["path"], 1)],
        },
        "not_exercised": not_exercised,
        "unresolved": {
            "placeholders": [variable["name"] for variable in suite["variables"]
                             if variable["kind"] == "unresolved"],
            "unmatched_requests": [{"source": entry["source"], "method": entry["method"],
                                    "path": entry["path"]}
                                   for entry in suite["requests"] if entry["route"] is None],
            "unparsed_sections": suite["unparsed"],
        },
    }


def extract() -> dict:
    return operations()
