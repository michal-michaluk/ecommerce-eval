"""Extractors for the source-derived documentation datasets.

Each module produces one dataset, holds paths and parsing code only, and carries no document
text: headings, labels, captions and sentences live in ``docs/templates/``.
"""

from __future__ import annotations

DATASETS = (
    "openapi",
    "persistence",
    "payloads",
    "examples",
    "events",
    "deployment",
    "observability",
    "security",
    "adr",
    "git_history",
)
