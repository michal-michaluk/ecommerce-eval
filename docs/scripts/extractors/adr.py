"""Index over the architecture decision records, when the repository keeps any.

Reusable as it stands: the directory and the file-name pattern come from ``adr`` in
``docs/scripts/config.json``, and an absent directory renders an empty index rather than
an invented one.
"""

from __future__ import annotations

import re

from . import common

DIRECTORY = common.config.DATA["adr"]["directory"]
FILENAME = re.compile(common.config.DATA["adr"]["filename"])
FRONT_MATTER_KEY = re.compile(r"^([a-z_]+):\s*(.*)$")


def _front_matter(text: str) -> dict:
    if not text.startswith("---"):
        return {}
    body = text.split("---", 2)[1] if text.count("---") >= 2 else ""
    values = {}
    for line in body.splitlines():
        match = FRONT_MATTER_KEY.match(line.strip())
        if match:
            values[match.group(1)] = match.group(2).strip().strip("\"'")
    return values


def extract() -> dict:
    directory = common.REPO / DIRECTORY
    if not directory.is_dir():
        return {"directory": DIRECTORY, "present": False, "decisions": []}
    decisions = []
    for path in sorted(directory.glob("*.md")):
        match = FILENAME.match(path.name)
        if not match:
            continue
        text = path.read_text(encoding="utf-8")
        front = _front_matter(text)
        heading = next((line.lstrip("# ").strip() for line in text.splitlines() if line.startswith("# ")), None)
        decisions.append({
            "id": match.group(1),
            "file": common.rel(path),
            "title": front.get("title") or heading,
            "status": front.get("status"),
            "date": front.get("date"),
            "deciders": front.get("deciders"),
            "evidence": [common.ref(common.rel(path), 1)],
        })
    return {"directory": DIRECTORY, "present": True, "decisions": decisions}
