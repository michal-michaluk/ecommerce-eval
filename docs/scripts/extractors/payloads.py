"""The JSON payload examples recovered from the test-source text blocks.

Starting point: payload examples read out of text blocks in tests. List the files that carry
them under ``sources.json_example_sources`` in ``docs/scripts/config.json``, or replace this
module with one that reads fixtures from wherever the adopter keeps them.
"""

from __future__ import annotations

import re

from . import common


def extract() -> dict:
    examples = []
    for source in common.JSON_EXAMPLE_SOURCES:
        for block in common.java_text_blocks(source):
            if not common.looks_like_json(block["content"]):
                continue
            parsed = common.parse_json(block["content"])
            examples.append({
                "index": len(examples) + 1,
                "path": source,
                "start": block["start"],
                "end": block["end"],
                "content": block["content"],
                "parsed": parsed,
                "placeholder": "%s" in block["content"],
                "keys": list(parsed) if isinstance(parsed, dict) else [],
                "field_paths": common.field_paths(parsed) if parsed is not None else [],
                "test_class": block["class"],
                "test_method": block["method"],
                "assertion": _assertion(block["opener"]),
                "evidence": [common.ref(source, block["start"], block["end"])],
            })
    return {"sources": list(common.JSON_EXAMPLE_SOURCES), "payloads": examples}


def _assertion(opener: str) -> str | None:
    for name in ("isExactlyLike", "hasFieldsLike"):
        if name in opener:
            return name
    return None


def with_containers(payloads: dict, persistence: dict) -> dict:
    """Attach the tables and JSON columns declared by the entity sources each example's test exercises."""
    adapting = {item["path"] for item in persistence["adapters"]} | {
        entry["path"] for item in persistence["json_columns"] for entry in item["entity_paths"]
    }
    declared: dict[str, str] = {}
    for path in common.java_files():
        name = common.rel(path)
        if name not in adapting:
            continue
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(r"\b(?:class|interface|record)\s+(\w+)", text):
            declared.setdefault(match.group(1), name)
    test_text: dict[str, str] = {}
    for example in payloads["payloads"]:
        text = test_text.setdefault(example["path"], common.read(example["path"]))
        types = sorted({name for name in re.findall(r"\b([A-Z][A-Za-z0-9]*)\b", text) if name in declared})
        files = sorted({declared[name] for name in types})
        example["entity_types"] = types
        example["tables"] = sorted(
            {item["table"] for item in persistence["adapters"] if item["path"] in files}
        )
        example["json_columns"] = [
            {"table": item["table"], "column": item["column"]}
            for item in persistence["json_columns"]
            if item["path"] in files or any(entry["path"] in files for entry in item["entity_paths"])
        ]
    return payloads
