"""Load ``config.json`` — the single file an adopter edits.

Every path the pipeline touches is data here, never prose in a script. Relative paths in the
config are relative to the repository root; ``repository_root`` itself is relative to this
directory. Set ``DOCS_CONFIG`` to point at a config elsewhere.
"""

from __future__ import annotations

import json
import os
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_CONFIG = HERE / "config.json"


def load() -> dict:
    path = Path(os.environ.get("DOCS_CONFIG", DEFAULT_CONFIG))
    if not path.is_file():
        raise SystemExit(f"missing pipeline config {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def root() -> Path:
    """The repository the pipeline documents."""
    return (HERE / DATA["repository_root"]).resolve()


def path(*keys: str) -> Path:
    """A configured path, resolved against the repository root."""
    value: object = DATA
    for key in keys:
        if not isinstance(value, dict) or key not in value:
            raise SystemExit(f"config.json: missing key {'/'.join(keys)}")
        value = value[key]
    if not isinstance(value, str):
        raise SystemExit(f"config.json: {'/'.join(keys)} is not a path")
    return root() / value


DATA = load()
DOCS = path("docs_root")
