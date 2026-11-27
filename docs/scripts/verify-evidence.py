#!/usr/bin/env python3
"""Resolve every invisible ``<!-- evidence: path:line -->`` marker in the documentation sources.

Every claim in ``docs/system/``, ``docs/integrator/``, ``docs/runbook/`` and ``docs/generated/`` is followed by an
HTML comment listing repository-relative ``path:line`` or ``path:start-end`` references. The
comment is stripped before rendering, so it never reaches a reader, but it is what makes a claim
checkable: this script asserts that every reference names an existing file and a line that exists,
and that the cited line is neither blank nor a bare ``package``/``import`` declaration, which would
prove nothing.

A violation is reported with the segment path, the ordinal of the comment inside that segment and
the offending reference. Any violation exits 1, so the gate fails instead of the review.

Usage
-----
    python3 docs/scripts/verify-evidence.py [--root docs]

Nothing is written: the script only reads.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import config  # noqa: E402

REPO = config.root()
SEGMENTS = tuple(config.DATA["authored_dirs"])

COMMENT = re.compile(r"<!--\s*evidence:(?P<body>.*?)-->", re.S)
REFERENCE = re.compile(r"^(?P<path>[^:\s][^:]*):(?P<start>\d+)(?:-(?P<end>\d+))?$")
COMMIT = re.compile(r"^git:(?P<sha>[0-9a-f]{40})$")
MEANINGLESS = re.compile(r"^\s*(package\s|import\s|$)")
OPERATOR = re.compile(r"^operator-input#(?P<id>\d+)$")
GAP = re.compile(r"^gap:\s*GAP-(?P<id>\d+)$")


def references(comment: str) -> list[str]:
    return [part.strip() for part in comment.split(",") if part.strip()]


def problem(reference: str) -> str | None:
    """Why ``reference`` does not prove anything, or ``None`` when it resolves."""
    commit = COMMIT.match(reference)
    if commit:
        return None if _commit_exists(commit.group("sha")) else f"commit {commit.group('sha')} is unknown"
    operator = OPERATOR.match(reference)
    if operator:
        answers = REPO / "docs" / "decisions" / "operator-input.md"
        if not answers.is_file():
            return "docs/decisions/operator-input.md does not exist"
        row = re.compile(r"^\|\s*" + operator.group("id") + r"\s*\|", re.M)
        return None if row.search(answers.read_text(encoding="utf-8")) else (
            f"operator-input#{operator.group('id')} is not recorded in docs/decisions/operator-input.md")
    gap = GAP.match(reference)
    if gap:
        wanted = "GAP-" + gap.group("id")
        for segment in REPO.joinpath("docs").rglob("*.md"):
            if wanted in segment.read_text(encoding="utf-8", errors="ignore"):
                return None
        return f"{wanted} is not defined anywhere in docs/"
    match = REFERENCE.match(reference)
    if not match:
        return "not a path:line reference"
    path = REPO / match.group("path")
    if not path.is_file():
        return f"{match.group('path')} does not exist"
    lines = path.read_text(encoding="utf-8").splitlines()
    start = int(match.group("start"))
    end = int(match.group("end") or start)
    if start < 1 or end < start or end > len(lines):
        return f"{match.group('path')} has {len(lines)} lines, {start}-{end} is out of range"
    for number in {start, end}:
        if MEANINGLESS.match(lines[number - 1]):
            return f"{match.group('path')}:{number} is blank, a package or an import line"
    return None


def _commit_exists(sha: str) -> bool:
    """A history row cites a commit, not a line; ask git read-only whether the commit is reachable."""
    result = subprocess.run(
        ["git", "-C", str(REPO), "cat-file", "-e", f"{sha}^{{commit}}"],
        capture_output=True,
    )
    return result.returncode == 0


def dataset_evidence(root: Path) -> list[tuple[str, str]]:
    """Every ``path:line`` reference a dataset carries, so provenance is gated before it is rendered.

    The directory is the configured ``data_dir``, not a name this module knows: a second configuration of
    the pipeline writes its datasets elsewhere, and a hard-coded ``generated/data`` would silently verify
    another docset's JSON while claiming to have checked this one.
    """
    found: list[tuple[str, str]] = []
    for path in sorted(config.path("data_dir").glob("*.json")):
        document = json.loads(path.read_text(encoding="utf-8"))
        for reference in _walk_evidence(document):
            found.append((path.relative_to(REPO).as_posix(), reference))
    return found


def _walk_evidence(value) -> list[str]:
    references: list[str] = []
    if isinstance(value, dict):
        for key, nested in value.items():
            if key == "evidence" and isinstance(nested, list):
                references.extend(item for item in nested if isinstance(item, str))
            else:
                references.extend(_walk_evidence(nested))
    elif isinstance(value, list):
        for item in value:
            references.extend(_walk_evidence(item))
    return references


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="verify-evidence.py",
        description="Resolve every evidence marker in the documentation segments. Reads only.",
    )
    parser.add_argument("--root", default="docs", metavar="DIR",
                        help="documentation root to scan, relative to the repository (default: docs)")
    args = parser.parse_args(argv)

    root = REPO / args.root
    segments = sorted(
        path for directory in SEGMENTS for path in (root / directory).rglob("*.md")
    )
    if not segments:
        print(f"no segments under {root.relative_to(REPO)}", file=sys.stderr)
        return 1

    violations, comments, refs = [], 0, 0
    for segment in segments:
        text = segment.read_text(encoding="utf-8")
        for index, match in enumerate(COMMENT.finditer(text), start=1):
            comments += 1
            for reference in references(match.group("body")):
                refs += 1
                reason = problem(reference)
                if reason:
                    violations.append(
                        f"{segment.relative_to(REPO)} comment {index}: {reference}: {reason}"
                    )

    checked = refs
    for source, reference in dataset_evidence(root):
        checked += 1
        reason = problem(reference)
        if reason:
            violations.append(f"{source}: {reference}: {reason}")

    for violation in violations:
        print(violation, file=sys.stderr)
    if violations:
        print(f"evidence: {len(violations)} violation(s) in {len(segments)} segments", file=sys.stderr)
        return 1
    print(f"evidence: {checked} references in {comments} comments and "
          f"{len({source for source, _ in dataset_evidence(root)})} datasets resolve")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
