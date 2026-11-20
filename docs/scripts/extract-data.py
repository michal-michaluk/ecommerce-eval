#!/usr/bin/env python3
"""Run every extractor and write the datasets the renderer consumes.

The pipeline is two-staged: this script is stage one and emits **data only**, the renderer binds
that data to the templates under ``docs/templates/``. No module involved in stage one contains a
heading, a label, a caption or a sentence; a dataset record carries facts and provenance
(``evidence``: repository-relative ``path:line`` references) and nothing else.

Datasets are written to ``docs/generated/data/`` with sorted keys, two-space indentation and a
trailing newline, so two runs over the same repository state are byte-identical. Nothing here
connects to a database or the network, runs the application, or writes outside
``docs/generated/data/``.

Modes
-----
(default)          write every dataset under docs/generated/data/
--check            smoke every extractor, then fail when a committed dataset differs, write nothing
--print <dataset>  extract and print one dataset, write nothing
--smoke            run every extractor in isolation under a wall-clock limit, write nothing
"""

from __future__ import annotations

import argparse
import json
import signal
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import config  # noqa: E402
from extractors import DATASETS, adr, deployment, events, examples, git_history  # noqa: E402
from extractors import observability, openapi, payloads, persistence, security  # noqa: E402

REPO = config.root()
DATA_DIR = config.path("data_dir")

SMOKE_LIMIT_SECONDS = 20


class ExtractionTimeout(RuntimeError):
    pass


def _build(name: str, cache: dict[str, dict]) -> dict:
    """Extract one dataset, building only the datasets it depends on."""
    if name in cache:
        return cache[name]
    if name == "openapi":
        result = openapi.extract()
    elif name == "persistence":
        result = persistence.extract()
    elif name == "payloads":
        result = payloads.with_containers(payloads.extract(), _build("persistence", cache))
    elif name == "examples":
        result = examples.extract()
    elif name == "events":
        result = events.extract()
    elif name == "deployment":
        result = deployment.extract()
    elif name == "observability":
        result = observability.extract_standalone()
    elif name == "security":
        result = security.extract()
    elif name == "adr":
        result = adr.extract()
    elif name == "git_history":
        result = git_history.extract()
    else:
        raise SystemExit(f"unknown dataset: {name}")
    cache[name] = result
    return result


def build() -> dict[str, dict]:
    """Run the extractors in dependency order and return every dataset."""
    cache: dict[str, dict] = {}
    return {name: _build(name, cache) for name in DATASETS}


def serialise(dataset: object) -> str:
    return json.dumps(dataset, indent=2, sort_keys=True, ensure_ascii=False) + "\n"


def target(name: str) -> Path:
    return DATA_DIR / f"{name}.json"


def counts(datasets: dict[str, dict]) -> dict[str, int]:
    """The non-negotiable extraction counts, so a regression fails the gate instead of the review."""
    return {
        "tables": len(datasets["persistence"]["tables"]),
        "changesets": len(datasets["persistence"]["changesets"]),
        "jsonb_columns": len(datasets["persistence"]["json_columns"]),
        "payload_examples": len(datasets["payloads"]["payloads"]),
        "request_response_blocks": len(datasets["examples"]["examples"]),
        "domain_events": len(datasets["events"]["events"]),
        "business_operations": sum(
            1
            for path, item in datasets["openapi"]["paths"].items()
            if not path.startswith("/actuator")
            for method in item
            if method.upper() in {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"}
        ),
    }


EXPECTED = config.DATA["expected_counts"]


def verify_counts(datasets: dict[str, dict]) -> list[str]:
    observed = counts(datasets)
    return [
        f"{name}: extracted {observed[name]}, expected {expected}"
        for name, expected in EXPECTED.items()
        if observed[name] != expected
    ]


def _alarm(_signum, _frame):
    raise ExtractionTimeout(f"extractor did not finish within {SMOKE_LIMIT_SECONDS}s")


def smoke() -> int:
    """Run every extractor on its own, so one broken module cannot hide behind another."""
    failures, total = [], 0
    signal.signal(signal.SIGALRM, _alarm)
    for name in DATASETS:
        started = time.monotonic()
        signal.setitimer(signal.ITIMER_REAL, SMOKE_LIMIT_SECONDS)
        try:
            _build(name, {})
            elapsed = time.monotonic() - started
            print(f"{name}: ok ({elapsed:.1f}s)")
            total += 1
        except ExtractionTimeout as error:
            failures.append(name)
            print(f"{name}: {error}")
        except SystemExit as error:  # an extractor refuses to guess by exiting; the sweep continues
            failures.append(name)
            print(f"{name}: {error}")
        except Exception as error:  # noqa: BLE001 - a broken extractor must not abort the sweep
            failures.append(name)
            print(f"{name}: {type(error).__name__}: {error}")
        finally:
            signal.setitimer(signal.ITIMER_REAL, 0)
    if failures:
        print(f"smoke failed for: {', '.join(failures)}", file=sys.stderr)
        return 1
    print(f"smoke: {total} extractors ok")
    return 0


def run_check() -> int:
    if smoke() != 0:
        return 1
    cache: dict[str, dict] = {}
    for name in DATASETS:
        try:
            dataset = _build(name, cache)
        except Exception as error:  # noqa: BLE001 - name the dataset that fails
            print(f"{name}: extraction failed: {type(error).__name__}: {error}", file=sys.stderr)
            return 1
        if not target(name).is_file():
            print(f"{name}: {target(name).relative_to(REPO)} is missing", file=sys.stderr)
            return 1
        if target(name).read_text(encoding="utf-8") != serialise(dataset):
            print(f"{name}: {target(name).relative_to(REPO)} differs from the extracted data",
                  file=sys.stderr)
            print("run: python3 docs/scripts/extract-data.py", file=sys.stderr)
            return 1
    problems = verify_counts(cache)
    if problems:
        print("extraction counts changed:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    print("every dataset matches the repository state")
    return 0


def run_write(datasets: dict[str, dict]) -> int:
    problems = verify_counts(datasets)
    if problems:
        print("extraction counts changed:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    for name in DATASETS:
        target(name).write_text(serialise(datasets[name]), encoding="utf-8")
        print(f"wrote {target(name).relative_to(REPO)}")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="extract-data.py",
        description="Extract the documentation datasets (data only, no document text) from the "
        "repository into docs/generated/data/. Read-only outside that directory; never connects "
        "to a database or the network and never runs the application.",
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true",
                      help="fail when a committed dataset differs from the extracted data")
    mode.add_argument("--print", dest="print_name", metavar="DATASET", choices=DATASETS,
                      help="print one dataset to stdout: " + ", ".join(DATASETS))
    mode.add_argument("--smoke", action="store_true",
                      help=f"run every extractor in isolation under a {SMOKE_LIMIT_SECONDS}s limit")
    args = parser.parse_args(argv)

    if args.smoke:
        return smoke()
    if args.print_name:
        sys.stdout.write(serialise(_build(args.print_name, {})))
        return 0
    if args.check:
        return run_check()
    return run_write(build())


if __name__ == "__main__":
    raise SystemExit(main())
