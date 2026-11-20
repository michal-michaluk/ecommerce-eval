#!/usr/bin/env python3
"""Resolve every relative markdown link and image reference in a documentation tree.

A document set fails quietly: a renamed segment, a moved diagram or a deleted appendix leaves a
link that still looks like a link and leads nowhere. Quarto does not fail on an unreachable image
path, and neither does a review of the PDF, because the caption is still typeset.

Scanned: every ``*.md`` under the configured ``authored_dirs`` and under ``chapters_dir``, or under
``--root`` when given. Skipped: absolute URLs, in-page anchors, and anything inside a fenced code
block or an inline code span — an example of a link is not a link.

A reference resolves when the path exists relative to the file that names it, or — as Quarto
resolves the paths of an included segment — relative to the document root. Both are tried, because a
chapter under ``system/`` writes ``generated/diagrams/x.svg`` and means the one under the document
root, not one under ``system/``. An image is a reference like any other, so a diagram whose SVG was
never rendered fails here.

Usage
-----
    python3 docs/scripts/check-links.py
    python3 docs/scripts/check-links.py --root docs/templates
    python3 docs/scripts/check-links.py --allow-image 'generated/diagrams/'   # artifact paths
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import config  # noqa: E402

ROOT = config.root()

FENCE = re.compile(r"```.*?```|~~~.*?~~~", re.S)
CODE = re.compile(r"`[^`\n]*`")
LINK = re.compile(r"(?<!!)\[[^\]]*\]\(([^)\s]+)\)")
IMAGE = re.compile(r"!\[[^\]]*\]\(([^)\s]+)\)")
EXTERNAL = ("http://", "https://", "#", "mailto:", "tel:")


def targets(text: str) -> list[tuple[str, bool]]:
    stripped = CODE.sub("", FENCE.sub("", text))
    found = [(match.group(1), False) for match in LINK.finditer(stripped)]
    found += [(match.group(1), True) for match in IMAGE.finditer(stripped)]
    return found


def documents(root: Path) -> list[Path]:
    return sorted(path for path in root.rglob("*.md") if path.is_file())


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="check-links.py", description=__doc__.splitlines()[0])
    parser.add_argument("--root", help="directory to scan instead of the configured document tree")
    parser.add_argument("--base", metavar="DIR",
                        help="document root that include paths are relative to (default: the scan root)")
    parser.add_argument("--allow-image", action="append", default=[], metavar="PREFIX",
                        help="skip image references whose path starts with PREFIX (generated artifacts)")
    args = parser.parse_args(argv)

    if args.root:
        roots = [ROOT / args.root]
    else:
        roots = [ROOT / config.DATA["docs_root"] / name for name in config.DATA["authored_dirs"]]
        roots.append(config.path("chapters_dir"))

    if args.base:
        base = ROOT / args.base
    elif args.root:
        base = ROOT / args.root
    else:
        base = ROOT / config.DATA["docs_root"]

    broken, checked = [], 0
    for root in roots:
        if not root.is_dir():
            continue
        for document in documents(root):
            text = document.read_text(encoding="utf-8")
            for target, is_image in targets(text):
                if target.startswith(EXTERNAL):
                    continue
                if is_image and any(target.startswith(prefix) for prefix in args.allow_image):
                    continue
                checked += 1
                relative = target.split("#")[0]
                if not (document.parent / relative).exists() and not (base / relative).exists():
                    broken.append(f"{document.relative_to(ROOT).as_posix()} -> {target}")

    for entry in broken:
        print(f"broken link: {entry}", file=sys.stderr)
    if broken:
        print(f"links: {len(broken)} of {checked} unresolved", file=sys.stderr)
        return 1
    print(f"links: {checked} references resolve")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
