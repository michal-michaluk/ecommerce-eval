"""Revision history derived from git log, classified and stripped of noise.

The same repository state must produce the same dataset: nothing here records the current time,
and ``git log`` order is stable.
"""

from __future__ import annotations

import re
import subprocess
from pathlib import Path

from . import common

CONVENTIONAL = re.compile(
    r"^(feat|fix|docs|chore|refactor|test|build|ci|perf|style)(?:\(([^)]+)\))?(!)?:\s*(.+)$"
)
MERGE = re.compile(r"^Merge pull request #(\d+) from \S+")
PATH_CATEGORIES = (
    (re.compile(r"(^|/)src/main/resources/db/|(^|/)db\.changelog"), "schema"),
    (re.compile(r"(^|/)k8s/"), "deployment"),
    (re.compile(r"(^|/)src/main/"), "behaviour"),
    (re.compile(r"(^|/)docs/"), "documentation"),
)
PATH_LABELS = {category for _pattern, category in PATH_CATEGORIES}
NOISE = (
    ("dependency bump", re.compile(r"(?i)^(bump|chore\(deps\)|dependabot)")),
    ("dependency bump", re.compile(r"(?i)\bbump .* from \S+ to \S+")),
    ("formatting", re.compile(r"(?i)^(re)?format(ting|ted)?\b")),
    ("work in progress", re.compile(r"(?i)^wip\b")),
    ("revert of a revert", re.compile(r'(?i)^Revert "Revert ')),
)
ENTRY_SEPARATOR = "\x1e"
FIELD_SEPARATOR = "\x1f"


def _git(*args: str) -> str:
    return subprocess.run(
        ["git", "--no-pager", *args], cwd=common.REPO, capture_output=True, text=True, check=True
    ).stdout


def _scope() -> str | None:
    """The path of the documented repository inside the git repository that contains it."""
    root = Path(_git("rev-parse", "--show-toplevel").strip()).resolve()
    try:
        return str(common.REPO.resolve().relative_to(root))
    except ValueError:
        return None


def _commits(scope: str | None) -> list[dict]:
    pathspec = ["--", f":(top){scope}"] if scope and scope != "." else []
    raw = _git("log", "--date=short", f"--format=%H{FIELD_SEPARATOR}%ad{FIELD_SEPARATOR}%P"
               f"{FIELD_SEPARATOR}%s{FIELD_SEPARATOR}%b{ENTRY_SEPARATOR}", *pathspec)
    commits = []
    for entry in raw.split(ENTRY_SEPARATOR):
        entry = entry.strip("\n")
        if not entry:
            continue
        sha, date, parents, subject, body = entry.split(FIELD_SEPARATOR, 4)
        commits.append({"sha": sha, "date": date, "parents": parents.split(), "subject": subject,
                        "body": body})
    return commits


def _touched(sha: str) -> list[str]:
    return [line for line in _git("show", "--name-only", "--format=", "--no-renames", sha).splitlines() if line]


def _path_category(paths: list[str]) -> str:
    for pattern, category in PATH_CATEGORIES:
        if any(pattern.search(path) for path in paths):
            return category
    return "other"


def _merge_title(commit: dict) -> str | None:
    if len(commit["parents"]) < 2:
        return None
    for line in commit["body"].splitlines():
        if line.strip():
            return line.strip()
    return None


def _normalise(text: str) -> str:
    return re.sub(r"[^a-z0-9 ]+", "", re.sub(r"\s+", " ", text.lower())).strip()


def _classify(change: str, paths: list[str]) -> tuple[str, str | None]:
    match = CONVENTIONAL.match(change)
    if match:
        return match.group(1), match.group(2)
    return _path_category(paths), None


def extract() -> dict:
    repo_scope = _scope()
    commits = _commits(repo_scope)
    dropped: dict[str, int] = {}
    rows: list[dict] = []
    seen: set[tuple[str | None, str]] = set()

    def drop(reason: str) -> None:
        dropped[reason] = dropped.get(reason, 0) + 1

    for commit in commits:
        pr = MERGE.match(commit["subject"])
        merge_title = _merge_title(commit)
        change = merge_title if merge_title and pr else commit["subject"]
        reference = {"sha": commit["sha"][:7]}
        if pr:
            reference["pull_request"] = int(pr.group(1))

        reason = next((name for name, pattern in NOISE if pattern.search(change)), None)
        if reason:
            drop(reason)
            continue

        paths = [] if CONVENTIONAL.match(change) else _touched(commit["sha"])
        category, scope = _classify(change, paths)
        match = CONVENTIONAL.match(change)
        text = match.group(4) if match else change
        if category == "style":
            drop("formatting")
            continue
        key = (scope, _normalise(text))
        if merge_title and key in seen:
            drop("merge duplicating a classified subject")
            continue
        if key in seen:
            drop("duplicate subject")
            continue
        seen.add(key)
        rows.append({
            "date": commit["date"],
            "scope": repo_scope,
            "category": category,
            "change": text,
            "conventional": bool(match),
            "ref": reference,
            "evidence": ["git:" + commit["sha"]],
        })

    counts: dict[str, int] = {}
    for row in rows:
        counts[row["category"]] = counts.get(row["category"], 0) + 1
    other = counts.get("other", 0)
    conventional = sum(1 for row in rows if row["conventional"])
    total = len(rows)
    return {
        "repository": _root_name(),
        "scope": repo_scope,
        "source": "git log",
        "commits_read": len(commits),
        "rows": [
            {key: value for key, value in row.items() if key != "conventional"} for row in rows
        ],
        "quality": {
            "counts": dict(sorted(counts.items())),
            "rows": total,
            "other": other,
            "other_share": round(other / total, 3) if total else 0.0,
            "conventional": conventional,
            "conventional_share": round(conventional / total, 3) if total else 0.0,
            "non_conventional": total - conventional,
            "non_conventional_share": round((total - conventional) / total, 3) if total else 0.0,
            "path_classified": sum(1 for row in rows if row["category"] in PATH_LABELS),
            "dropped": dict(sorted(dropped.items())),
        },
    }


def _root_name() -> str:
    """The repository the history belongs to, taken from git, not from the path of this module."""
    return Path(_git("rev-parse", "--show-toplevel").strip()).name
