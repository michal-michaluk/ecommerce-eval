#!/usr/bin/env python3
"""Bind the extracted datasets to the Jinja2 templates under ``docs/templates/``.

Stage two of the pipeline. It reads only ``docs/generated/data/`` (written by
``extract-data.py``) and ``docs/templates/``, and writes only ``docs/generated/chapters/*.md``.
Every heading, label, caption and empty state the reader sees comes from a template; this module
holds file names and wiring only, never document text.

Provenance reaches the segments as invisible ``<!-- evidence: path:line -->`` comments emitted
by a template macro, so a reviewer of the sources can resolve every claim while the rendered
deliverable carries none of it.

Determinism: templates iterate the dataset lists in their extracted order, the chapter plan is
sorted, and nothing here reads the clock, the network or the environment. Two runs over the
same repository state are byte-identical.

Modes
-----
(default)          write every segment under docs/generated/chapters/
--check            fail when a committed segment differs from the rendered one, write nothing
--print <segment>  write one segment to stdout, write nothing
--list             print the segment names this run would write, write nothing
"""

from __future__ import annotations

import argparse
import json
import sys
import re
from pathlib import Path

from jinja2 import Environment, FileSystemLoader, StrictUndefined

sys.path.insert(0, str(Path(__file__).resolve().parent))

import config  # noqa: E402

REPO = config.root()
DOCS = config.DOCS
DATA_DIR = config.path("data_dir")
GENERATED_DIR = config.path("chapters_dir")
TEMPLATE_DIR = config.path("templates_dir")

OPERATION_CHAPTERS = (
    ("operation", "chapters/operation.md.tmpl", "op-{chapter}.md"),
    ("operation-fixtures", "chapters/operation-fixtures.md.tmpl", "fx-{chapter}.md"),
)

STANDALONE_CHAPTERS = (
    ("schema", "chapters/schema.md.tmpl", "schema.md"),
    ("migrations", "chapters/migrations.md.tmpl", "migrations.md"),
    ("payload-columns", "chapters/payload-columns.md.tmpl", "payload-columns.md"),
    ("security-policy", "chapters/security-policy.md.tmpl", "security-policy.md"),
    ("appendix-payloads", "chapters/appendix-payloads.md.tmpl", "appendix-payloads.md"),
    ("appendix-fixtures", "chapters/appendix-fixtures.md.tmpl", "appendix-fixtures.md"),
    ("deployment", "chapters/deployment.md.tmpl", "deployment.md"),
    ("telemetry", "chapters/telemetry.md.tmpl", "telemetry.md"),
    ("revision-history", "chapters/revision-history.md.tmpl", "revision-history.md"),
    ("appendix-decisions", "chapters/appendix-decisions.md.tmpl", "appendix-decisions.md"),
)

API_CHAPTERS = (
    ("api-operation", "chapters/api-operation.md.tmpl", "api-{chapter}.md"),
    ("api-operational", "chapters/api-operational.md.tmpl", "api-operational.md"),
)

API_METHODS = ("get", "put", "post", "patch", "delete", "head", "options")
API_OPERATIONAL = "/actuator"
API_EXCERPT_LINES = 12
API_CONSTRAINTS = (
    "minLength", "maxLength", "pattern", "minimum", "maximum",
    "minItems", "maxItems", "uniqueItems", "default",
)


DATASETS = ("persistence", "payloads", "examples", "security", "openapi",
            "deployment", "observability", "git_history", "adr")


def datasets() -> dict[str, dict]:
    missing = [name for name in DATASETS if not (DATA_DIR / f"{name}.json").is_file()]
    if missing:
        raise SystemExit(
            "missing dataset(s): "
            + ", ".join(missing)
            + "; run: python3 docs/scripts/extract-data.py"
        )
    return {
        name: json.loads((DATA_DIR / f"{name}.json").read_text(encoding="utf-8"))
        for name in DATASETS
    }


def labels() -> dict:
    path = TEMPLATE_DIR / "labels.yml"
    import yaml

    return yaml.safe_load(path.read_text(encoding="utf-8")) or {}


COMPOUND = re.compile(r"(?<=[a-z0-9])(?=[A-Z])")


def subject(text: str) -> str:
    """A commit subject with its path separators widened and its compound identifiers spelled out.

    The history is quoted, not rewritten: the words are the words of the commit. Only two
    typographic rules apply, so that the table names no location and no code identifier — a path
    separator becomes a middle dot and a compound word keeps its parts, in lower case.
    """
    words = []
    for word in (text or "").replace("/", " · ").split(" "):
        words.append(COMPOUND.sub(" ", word).lower() if COMPOUND.search(word) else word)
    return " ".join(words)


def environment() -> Environment:
    env = Environment(
        loader=FileSystemLoader(str(TEMPLATE_DIR)),
        undefined=StrictUndefined,
        autoescape=False,
        trim_blocks=True,
        lstrip_blocks=True,
        keep_trailing_newline=True,
    )
    env.filters["subject"] = subject
    return env


def plan(data: dict[str, dict]) -> list[tuple[str, str, str]]:
    """The segments this run writes: ``(template, file name, chapter kind)``, in a stable order."""
    jobs: list[tuple[str, str, str]] = []
    for kind, template, pattern in OPERATION_CHAPTERS:
        for operation in data["examples"]["operations"]:
            jobs.append((template, pattern.format(chapter=operation["chapter"]), kind))
    for kind, template, pattern in API_CHAPTERS:
        if kind == "api-operational":
            jobs.append((template, pattern, kind))
            continue
        for operation in api_operations(data):
            jobs.append((template, pattern.format(chapter=operation["chapter"]), kind))
    for kind, template, name in STANDALONE_CHAPTERS:
        jobs.append((template, name, kind))
    return jobs


def deref(schema: dict, schemas: dict, seen: tuple[str, ...] = ()) -> tuple[dict, list[str]]:
    """Resolve ``$ref``, merge ``allOf`` and the variants of ``oneOf`` into one structural schema.

    An ``allOf`` union keeps the ``required`` list of every part as well as the outer one. A
    ``oneOf`` collapses to the union of its variants' properties and reports the variant names. A
    reference cycle is cut inside one ``$ref``/``allOf`` chain, which is the only place this
    artifact could express one.
    """
    variants: list[str] = []
    while True:
        if "$ref" in schema:
            name = schema["$ref"].rsplit("/", 1)[-1]
            if name in seen:
                return {"type": "object"}, variants
            seen += (name,)
            schema = schemas.get(name, {})
            continue
        if "allOf" in schema:
            merged, properties, required = {"type": "object", "properties": {}}, {}, []
            for part in schema["allOf"]:
                resolved, nested_variants = deref(part, schemas, seen)
                properties.update(resolved.get("properties", {}))
                required.extend(resolved.get("required", []))
                variants.extend(nested_variants)
            merged["properties"] = properties
            required.extend(schema.get("required", []))
            if required:
                merged["required"] = required
            if "description" in schema:
                merged["description"] = schema["description"]
            schema = merged
            continue
        if "oneOf" in schema:
            properties = {}
            for part in schema["oneOf"]:
                variants.append(part["$ref"].rsplit("/", 1)[-1] if "$ref" in part else part.get("type", ""))
                properties.update(deref(part, schemas, seen)[0].get("properties", {}))
            schema = {"type": "object", "properties": properties}
            continue
        return schema, [variant for variant in variants if variant]


def scalar(value: object) -> str:
    """A declared value as JSON writes it, so a boolean constraint reads ``true`` and not ``True``."""
    if isinstance(value, bool):
        return "true" if value else "false"
    return str(value)


def constraints(schema: dict) -> list[str]:
    """The declared constraint keywords in a stable order, named as the artifact names them."""
    found = [f"{key} {scalar(schema[key])}" for key in API_CONSTRAINTS if key in schema]
    if schema.get("enum"):
        found.append("enum " + ", ".join(scalar(value) for value in schema["enum"]))
    return found


def field(path: str, schema: dict, schemas: dict, required: bool) -> tuple[dict, list[dict]]:
    """One row for ``schema`` at ``path``, plus the flattened rows of everything below it."""
    resolved, variants = deref(schema, schemas)
    items = resolved.get("items") if isinstance(resolved.get("items"), dict) else {}
    item, item_variants = deref(items, schemas) if items else ({}, [])
    map_value = resolved.get("additionalProperties") if isinstance(
        resolved.get("additionalProperties"), dict) else {}
    row = {
        "path": path,
        "type": resolved.get("type") or ("object" if resolved.get("properties") else ""),
        "item_type": item.get("type", "") or ("object" if item.get("properties") else ""),
        "format": resolved.get("format", ""),
        "required": required,
        "constraints": constraints(resolved) + constraints(item),
        "one_of": variants or item_variants,
        "description": schema.get("description") or resolved.get("description", ""),
    }
    children: list[dict] = []
    if item.get("properties"):
        array_prefix = f"{path}." if not path or path.endswith("[]") else f"{path}[]" + "."
        children = fields(item, schemas, array_prefix)
    elif resolved.get("properties"):
        children = fields(resolved, schemas, f"{path}.")
    elif map_value:
        map_path = f"{path}*" if path and not path.endswith("*") else "*"
        child, grandchildren = field(map_path, map_value, schemas, False)
        children = [child, *grandchildren]
    return row, children


def fields(schema: dict, schemas: dict, prefix: str = "") -> list[dict]:
    """Every property of ``schema`` flattened into dot paths, ordered by property name."""
    resolved, _ = deref(schema, schemas)
    required = set(resolved.get("required", []))
    rows: list[dict] = []
    for name in sorted(resolved.get("properties", {})):
        row, children = field(prefix + name, resolved["properties"][name], schemas, name in required)
        rows.extend([row, *children])
    return rows


def body_rows(schema: dict, schemas: dict) -> list[dict]:
    """Rows for a whole message body, including a top-level array or free-form object."""
    resolved, _ = deref(schema, schemas)
    if resolved.get("properties"):
        return fields(resolved, schemas)
    root = "[]" if resolved.get("type") == "array" else ("*" if resolved.get("additionalProperties") else "")
    row, children = field(root, resolved, schemas, False)
    rows = [row, *children]
    keys = [(item["path"], item["type"], item["description"]) for item in rows]
    return [item for index, item in enumerate(rows) if index == 0 or keys[index - 1] != keys[index]]


def parameter_rows(operation: dict, schemas: dict) -> list[dict]:
    """Path and query parameters, path first, each object-typed parameter flattened below itself."""
    rows: list[dict] = []
    for parameter in operation.get("parameters", []):
        schema = parameter.get("schema", {})
        row, children = field(parameter["name"], schema, schemas, bool(parameter.get("required")))
        rows.append({
            "name": parameter["name"],
            "in": parameter.get("in", ""),
            "type": row["type"],
            "item_type": row["item_type"],
            "format": row["format"],
            "required": row["required"],
            "constraints": row["constraints"],
            "one_of": row["one_of"],
            "description": parameter.get("description") or row["description"],
        })
        rows.extend({
            "name": child["path"], "in": parameter.get("in", ""), "type": child["type"],
            "item_type": child["item_type"], "format": child["format"],
            "required": child["required"], "constraints": child["constraints"],
            "one_of": child["one_of"], "description": child["description"],
        } for child in children)
    return sorted(rows, key=lambda row: (row["in"] != "path", row["name"]))


def media_tables(content: dict, schemas: dict) -> list[dict]:
    """One field table per distinct body, ordered by media type, the media types that share it listed."""
    tables: list[dict] = []
    for media_type in sorted(content):
        rows = body_rows(content[media_type].get("schema", {}), schemas)
        shared = next((table for table in tables if table["rows"] == rows), None)
        if shared is None:
            tables.append({"media_types": [media_type], "rows": rows})
        else:
            shared["media_types"].append(media_type)
    return tables


def matches(matcher: str, path: str) -> bool:
    """Whether a filter-chain pattern covers a path.

    A trailing ``/**`` is a prefix, a ``{variable}`` segment admits any value and every other segment
    must agree exactly.
    """
    if matcher.endswith("/**"):
        stem = matcher[:-3]
        return path == stem or path.startswith(stem + "/")
    declared = matcher.strip("/").split("/")
    actual = path.strip("/").split("/")
    if len(declared) != len(actual):
        return False
    return all(
        (segment.startswith("{") and segment.endswith("}")) or segment == value
        for segment, value in zip(declared, actual)
    )


def access(security: dict, method: str, path: str) -> dict:
    """The authorization decision the filter chain declares for this request, with its line.

    The artifact declares ``security`` only on the operations that carry a token, so the filter chain
    is what decides: a matching ``permitAll`` rule makes an operation public, the ``anyRequest``
    fallback makes it authenticated. A rule that names an HTTP method decides only that method. Nothing
    here assumes that an undeclared scheme means public.
    """
    chain = security.get("filter_chain", {})
    source = chain.get("path") or ""
    for rule in chain.get("rules", []):
        if rule.get("method") not in (None, method):
            continue
        if rule.get("matcher") and matches(rule["matcher"], path):
            return {
                "authenticated": rule["decision"] != "permitAll",
                "access_evidence": [f"{source}:{rule['line']}"] if source else [],
            }
    fallback = chain.get("fallback") or {}
    return {
        "authenticated": fallback.get("decision", "authenticated") != "permitAll",
        "access_evidence": [f"{source}:{fallback['line']}"] if source and fallback.get("line") else [],
    }


def merged_evidence(*groups: list[str]) -> list[str]:
    """The references of several claims, in the order they were made and without repetition."""
    merged: list[str] = []
    for group in groups:
        merged.extend(reference for reference in group if reference not in merged)
    return merged


def api_operations(data: dict[str, dict]) -> list[dict]:
    """The business operations of the artifact, ordered by path and then by method."""
    openapi = data["openapi"]
    schemas = openapi.get("components", {}).get("schemas", {})
    by_path_and_method = {
        (operation["path"], operation["method"].lower()): operation
        for operation in data["examples"]["operations"]
    }
    chapters = labels().get("api_chapters", {})
    ordered = sorted(
        (path, method)
        for path, item in openapi["paths"].items()
        if not path.startswith(API_OPERATIONAL)
        for method in item
        if method in API_METHODS
    )
    operations = []
    for index, (path, method) in enumerate(ordered):
        operation = openapi["paths"][path][method]
        example = by_path_and_method.get((path, method), {})
        operations.append({
            "number": chapters.get("first_operation", 1) + index,
            "chapter": example.get("chapter") or operation["operationId"],
            "method": method.upper(),
            "path": path,
            "summary": operation.get("summary", ""),
            "description": operation.get("description", ""),
            **access(data["security"], method.upper(), path),
            "consumes": sorted(operation.get("requestBody", {}).get("content", {})),
            "produces": sorted(
                media_type
                for response in operation.get("responses", {}).values()
                for media_type in response.get("content", {})
            ),
            "parameters": parameter_rows(operation, schemas),
            "request_tables": media_tables(operation.get("requestBody", {}).get("content", {}), schemas),
            "responses": [
                {
                    "status": status,
                    "description": operation["responses"][status].get("description", ""),
                    "tables": media_tables(operation["responses"][status].get("content", {}), schemas),
                }
                for status in sorted(operation.get("responses", {}))
            ],
            "evidence": merged_evidence(example.get("evidence", []),
                                        operation.get("summary_evidence", []),
                                        operation.get("description_evidence", [])),
        })
    return operations


def operational_operations(data: dict[str, dict]) -> list[dict]:
    """The framework operations of the actuator surface, ordered by path and then by method."""
    openapi = data["openapi"]
    schemas = openapi.get("components", {}).get("schemas", {})
    chapters = labels().get("api_chapters", {})
    number = chapters.get("operational", 0)
    entries = []
    for index, (path, method) in enumerate(sorted(
        (path, method)
        for path, item in openapi["paths"].items()
        if path.startswith(API_OPERATIONAL)
        for method in item
        if method in API_METHODS
    ), start=1):
        operation = openapi["paths"][path][method]
        entries.append({
            "number": f"{number}.{index}",
            "method": method.upper(),
            "path": path,
            "summary": operation.get("summary", ""),
            **access(data["security"], method.upper(), path),
            "parameters": parameter_rows(operation, schemas),
            "responses": [
                {
                    "status": status,
                    "description": operation["responses"][status].get("description", ""),
                    "tables": media_tables(operation["responses"][status].get("content", {}), schemas),
                }
                for status in sorted(operation.get("responses", {}))
            ],
        })
    return entries


def excerpt(content: str) -> dict:
    """The leading lines of a recorded block, verbatim, and how many lines the block has in total."""
    lines = content.splitlines()
    return {"lines": lines[:API_EXCERPT_LINES], "total": len(lines)}


def api_examples(operation: dict, data: dict[str, dict], label_map: dict) -> list[dict]:
    """One trimmed request excerpt and one trimmed response excerpt per recorded payload."""
    entries: list[dict] = []
    for block in request_bodies(operation, data["examples"]["examples"]):
        entries.append({
            "kind": "request",
            "appendix": "B",
            "entry": f"B.{block['index']}",
            "evidence": block["evidence"],
            **excerpt(block["body_raw"]),
        })
        break
    index = {f"{item['test_class']}#{item['test_method']}": item for item in data["payloads"]["payloads"]}
    for item in label_map.get("response_examples", {}).get(operation["chapter"], []):
        payload = index[item["payload"]]
        entries.append({
            "kind": "response",
            "note": item["note"],
            "appendix": "A",
            "entry": f"A.{payload['index']}",
            "evidence": payload["evidence"],
            **excerpt(payload["content"]),
        })
    return entries


def request_bodies(operation: dict, blocks: list[dict]) -> list[dict]:
    """The distinct request bodies the scenario sends to this operation, in scenario order."""
    bodies, seen = [], set()
    for block in blocks:
        if block["operation"] is None or block["operation"]["chapter"] != operation["chapter"]:
            continue
        if not block["body_raw"] or not (block["expected_status"] or "").startswith("2"):
            continue
        if block["body_raw"] in seen:
            continue
        seen.add(block["body_raw"])
        bodies.append(block)
    return bodies


def payload_index(payloads: dict) -> dict[str, dict]:
    """Payloads keyed by the test that asserts them, so a label names a payload, never an ordinal."""
    return {
        f"{example['test_class']}#{example['test_method']}": example
        for example in payloads["payloads"]
    }


def response_examples(operation: dict, payloads: dict, label_map: dict) -> list[dict]:
    entries = label_map.get("response_examples", {}).get(operation["chapter"], [])
    index = payload_index(payloads)
    missing = [entry["payload"] for entry in entries if entry["payload"] not in index]
    if missing:
        raise SystemExit(
            f"labels.yml names unknown payload(s) for {operation['chapter']}: " + ", ".join(missing)
        )
    return [
        {"note": entry["note"], "example": index[entry["payload"]]} for entry in entries
    ]


def manual_number(name: str) -> int:
    """The chapter number the composition gives a generated chapter of the operations manual."""
    return labels().get("manual_chapters", {})[name]


def context(kind: str, name: str, data: dict[str, dict], label_map: dict) -> dict:
    operations = {operation["chapter"]: operation for operation in data["examples"]["operations"]}
    blocks = data["examples"]["examples"]
    base = {"labels": label_map, "chapters": data["examples"]["operations"]}

    if kind == "api-operation":
        for operation in api_operations(data):
            if f"api-{operation['chapter']}.md" == name:
                source = operations[operation["chapter"]]
                return {
                    **base,
                    "operation": operation,
                    "excerpts": api_examples(source, data, label_map),
                    "api_evidence": ["docs/generated/data/openapi.json:1"],
                }
    if kind == "api-operational":
        return {
            **base,
            "operations": operational_operations(data),
            "api_evidence": ["docs/generated/data/openapi.json:1"],
        }
    if kind == "operation":
        for operation in data["examples"]["operations"]:
            if f"op-{operation['chapter']}.md" == name:
                return {
                    **base,
                    "operation": operation,
                    "request_bodies": request_bodies(operation, blocks),
                    "response_examples": response_examples(operation, data["payloads"], label_map),
                }
    if kind == "operation-fixtures":
        for operation in data["examples"]["operations"]:
            if f"fx-{operation['chapter']}.md" == name:
                fixtures = [
                    block for block in blocks
                    if block["operation"] is not None
                    and block["operation"]["chapter"] == operation["chapter"]
                ]
                return {**base, "operation": operation, "fixtures": fixtures}
    if kind == "schema":
        return {**base, "tables": data["persistence"]["tables"]}
    if kind == "migrations":
        return {**base, "changesets": data["persistence"]["changesets"]}
    if kind == "payload-columns":
        return {
            **base,
            "json_columns": data["persistence"]["json_columns"],
            "examples": data["payloads"]["payloads"],
        }
    if kind == "security-policy":
        return {**base, "filter_chain": data["security"]["filter_chain"]}
    if kind == "appendix-payloads":
        return {**base, "examples": data["payloads"]["payloads"]}
    if kind == "appendix-fixtures":
        return {**base, "fixtures": blocks}
    if kind == "deployment":
        return {**base, "deployment": data["deployment"], "number": manual_number("deployment")}
    if kind == "telemetry":
        return {**base, "observability": data["observability"], "number": manual_number("telemetry")}
    if kind == "revision-history":
        return {**base, "history": data["git_history"],
                "number": manual_number("revision_history")}
    if kind == "appendix-decisions":
        return {**base, "decisions": data["adr"]}
    raise SystemExit(f"no context for segment {name} of kind {kind}")


def render_all(only: str | None = None) -> dict[str, str]:
    data, label_map = datasets(), labels()
    env = environment()
    rendered: dict[str, str] = {}
    for template, name, kind in plan(data):
        if only is not None and name != only:
            continue
        rendered[name] = env.get_template(template).render(
            **context(kind, name, data, label_map)
        ).rstrip("\n") + "\n"
    if only is not None and not rendered:
        raise SystemExit(f"unknown segment: {only}")
    return rendered


def segment_name(value: str) -> str:
    return value if value.endswith(".md") else f"{value}.md"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="render-docs.py",
        description="Render the documentation segments from the extracted datasets and the "
        "templates under docs/templates/. Writes only docs/generated/chapters/*.md.",
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true",
                      help="fail when a committed segment differs from the rendered one")
    mode.add_argument("--print", dest="print_name", metavar="SEGMENT",
                      help="print one segment to stdout, with or without the .md suffix")
    mode.add_argument("--list", dest="list_names", action="store_true",
                      help="print the segment names this run writes")
    args = parser.parse_args(argv)

    rendered = render_all(segment_name(args.print_name) if args.print_name else None)

    if args.list_names:
        for name in sorted(rendered):
            print(name)
        return 0

    if args.print_name:
        sys.stdout.write(rendered[segment_name(args.print_name)])
        return 0

    if args.check:
        problems = []
        for name in sorted(rendered):
            path = GENERATED_DIR / name
            if not path.is_file():
                problems.append(f"{path.relative_to(REPO)} is missing")
            elif path.read_text(encoding="utf-8") != rendered[name]:
                problems.append(f"{path.relative_to(REPO)} differs from the templates")
        for name in sorted(p.name for p in GENERATED_DIR.glob("*.md")):
            if name not in rendered:
                problems.append(f"docs/generated/{name} is rendered by no template")
        if problems:
            print("generated segments are out of date:", file=sys.stderr)
            for problem in problems:
                print(f"  - {problem}", file=sys.stderr)
            print("run: python3 docs/scripts/render-docs.py", file=sys.stderr)
            return 1
        print(f"every generated segment matches its template ({len(rendered)} segments)")
        return 0

    for name in sorted(rendered):
        (GENERATED_DIR / name).parent.mkdir(parents=True, exist_ok=True)
        (GENERATED_DIR / name).write_text(rendered[name], encoding="utf-8")
        print(f"wrote docs/generated/chapters/{name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
