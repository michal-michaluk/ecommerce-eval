"""The domain events declared by the JSON subtype registration, with the table that persists them.

Starting point: the shape below is what a Jackson subtype registration yields. Point
``events.domain_event`` in ``docs/scripts/config.json`` at the class that registers the
subtypes and re-read this module against another event registry.
"""

from __future__ import annotations

import re

from . import common, persistence

DOMAIN_EVENT = common.config.DATA["events"]["domain_event"]

LISTENER = re.compile(
    r"@EventListener[^\n]*\n\s*(?:@\w+[^\n]*\n\s*)*public\s+void\s+(\w+)\s*\(\s*DomainEvent\.(\w+)\s+\w+\s*\)"
)


def _record_fields(text: str, name: str) -> dict:
    match = re.search(r"record\s+" + re.escape(name) + r"\s*\(", text)
    if not match:
        return {"fields": [], "line": None}
    open_paren = text.index("(", match.start())
    close = common.match_bracket(text, open_paren)
    fields = []
    for component in common.split_top_level(text[open_paren + 1 : close]):
        type_name, _, field_name = component.rpartition(" ")
        fields.append({"name": field_name.strip(), "type": type_name.strip()})
    return {"fields": fields, "line": common.line_at(text, match.start())}


def _handlers() -> list[dict]:
    """Every ``@EventListener`` that takes a domain event, with the class that declares it.

    The annotation is matched only when a method taking ``DomainEvent.<Name>`` follows it, so a comment
    that mentions the annotation is not a handler.
    """
    found: list[dict] = []
    for path in common.java_files():
        text = path.read_text(encoding="utf-8")
        if "@EventListener" not in text:
            continue
        for match in LISTENER.finditer(text):
            line = common.line_at(text, match.start())
            declaring = None
            for candidate in reversed(text[:match.start()].splitlines()):
                named = re.search(r"\b(?:class|interface|record)\s+(\w+)", candidate)
                if named:
                    declaring = named.group(1)
                    break
            found.append({"event": match.group(2), "method": match.group(1),
                          "handler": declaring, "path": common.rel(path), "line": line,
                          "evidence": [common.ref(common.rel(path), line)]})
    return found


def extract() -> dict:
    text = common.read(DOMAIN_EVENT)
    json_columns = [item for item in persistence.json_columns() if item["field_type"] == "DomainEvent"]
    handlers = _handlers()
    events = []
    for match in re.finditer(
        r"@JsonSubTypes\.Type\(\s*value\s*=\s*([\w.]+)\.class\s*,\s*name\s*=\s*\"([^\"]+)\"\s*\)", text
    ):
        java_type, wire_name = match.group(1), match.group(2)
        simple_name = java_type.rsplit(".", 1)[-1]
        record = _record_fields(text, simple_name)
        versions = wire_name.split("_v")
        events.append({
            "wire_name": wire_name,
            "wired_type": versions[0],
            "version": versions[1] if len(versions) > 1 else None,
            "java_type": java_type,
            "declared_in": {"path": DOMAIN_EVENT, "line": common.line_at(text, match.start())},
            "fields": record["fields"],
            "record_line": record["line"],
            "handlers": [item for item in handlers if item["event"] == simple_name],
            "persisted_as": [
                {"table": item["table"], "column": item["column"], "path": item["path"], "line": item["line"]}
                for item in json_columns
            ],
            "evidence": [common.ref(DOMAIN_EVENT, common.line_at(text, match.start()))],
        })
    return {
        "registration": {
            "path": DOMAIN_EVENT,
            "interface": "DomainEvent",
            "line": common.line_at(text, text.index("@JsonTypeInfo")),
            "evidence": [common.ref(DOMAIN_EVENT, common.line_at(text, text.index("@JsonTypeInfo")))],
        },
        "events": events,
        "handlers": handlers,
    }
