#!/usr/bin/env python3
"""Sync the generated OpenAPI document into the documentation tree.

Copies the OpenAPI artifact named by ``openapi.source`` in ``config.json`` to
``openapi.target``, rewriting every JSON object with its keys sorted, two-space indentation and a
trailing newline, so that two runs over the same build artifact are byte-identical. Nothing is
dropped: paths, operations, schemas and properties are only reordered.

Modes
-----
(default)  write ``docs/generated/data/openapi.json``
--check    compare the on-disk file with the normalised source, exit 1 on drift, write nothing
--print    write the normalised document to stdout, write nothing

The script reads only the build artifact and writes only under ``docs/generated/data/``.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import config  # noqa: E402

REPO = config.root()
SOURCE = config.path("openapi", "source")
TARGET = config.path("openapi", "target")


def normalise(document: object) -> str:
    return json.dumps(document, indent=2, sort_keys=True, ensure_ascii=False) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Sync the generated OpenAPI document.")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true",
                      help="exit 1 when docs/generated/data/openapi.json differs from the normalised source")
    mode.add_argument("--print", dest="print_document", action="store_true",
                      help="write the normalised document to stdout")
    args = parser.parse_args(argv)

    if not SOURCE.is_file():
        print(f"missing {SOURCE}: set openapi.source in config.json to the artifact the build "
              f"produces, then build it (a project without an HTTP contract leaves openapi.source "
              f"empty and skips this stage)",
              file=sys.stderr)
        return 2

    normalised = normalise(json.loads(SOURCE.read_text(encoding="utf-8")))

    if args.print_document:
        sys.stdout.write(normalised)
        return 0

    if args.check:
        current = TARGET.read_text(encoding="utf-8") if TARGET.is_file() else None
        if current != normalised:
            print(f"{TARGET.relative_to(REPO)} is out of date; "
                  f"run {Path(__file__).name}", file=sys.stderr)
            return 1
        return 0

    TARGET.parent.mkdir(parents=True, exist_ok=True)
    TARGET.write_text(normalised, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
