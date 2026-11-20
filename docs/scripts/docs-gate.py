#!/usr/bin/env python3
"""Run the documentation gate chain in order, one line per stage.

Each stage is the non-mutating mode of one pipeline script, so the chain is safe to run at any
time and fails on the first real drift. Every stage prints ``OK`` or ``FAIL`` with what it
measured; the exit status is 1 when any stage failed.

Stages
------
smoke       every extractor runs alone under a wall-clock limit
drift       every dataset and every generated chapter matches the repository state
evidence    every ``path:line`` reference resolves to a real, non-trivial line
diagram     every rendered SVG matches its Mermaid source
purity      no document text reaches the scripts, and every template resolves
links       every relative markdown link and image in the docset resolves
composition ``quarto render`` succeeds and resolves every cross-reference
artifacts   the rendered PDF and HTML carry no provenance and no forbidden identifier

Usage
-----
    python3 docs/scripts/docs-gate.py              # the whole chain
    python3 docs/scripts/docs-gate.py --fast       # skip composition and artifacts
    python3 docs/scripts/docs-gate.py --only drift  # one stage
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import config  # noqa: E402

HERE = Path(__file__).resolve().parent
PYTHON = sys.executable

STAGES = ("smoke", "drift", "evidence", "diagram", "purity", "links", "composition", "artifacts")

HEADING = re.compile(r"^\s*#{2,}\s")
CAPTION = re.compile(r"^\s*:\s+[A-Z]")


def run(*command: str, cwd: Path | None = None) -> tuple[bool, str]:
    result = subprocess.run(command, capture_output=True, text=True, cwd=cwd)
    return result.returncode == 0, (result.stdout + result.stderr).strip()


def script(name: str) -> str:
    return str(HERE / name)


def smoke(details: list[str]) -> bool:
    return run(PYTHON, script("extract-data.py"), "--smoke")[0]


def drift(details: list[str]) -> bool:
    commands = [(PYTHON, script("extract-data.py"), "--check"),
                (PYTHON, script("render-docs.py"), "--check")]
    if config.DATA["openapi"]["source"]:
        commands.insert(0, (PYTHON, script("sync-openapi.py"), "--check"))
    ok = True
    for command in commands:
        passed, output = run(*command)
        ok = ok and passed
        if not passed:
            details.append(output.splitlines()[0] if output else command[1])
    return ok


def evidence(details: list[str]) -> bool:
    passed, output = run(PYTHON, script("verify-evidence.py"))
    return passed or details.append(output.splitlines()[-1] if output else "unresolved reference")


def diagram(details: list[str]) -> bool:
    passed, output = run(PYTHON, script("render-diagrams.py"), "--check")
    return passed or details.append(output.splitlines()[0] if output else "stale diagram")


def purity(details: list[str]) -> bool:
    """No heading and no caption in a script; every template named by the renderer exists."""
    ok = True
    sources = sorted(HERE.glob("*.py")) + sorted((HERE / "extractors").glob("*.py"))
    for source in sources:
        for number, line in enumerate(source.read_text(encoding="utf-8").splitlines(), start=1):
            if line.lstrip().startswith("#!") or line.lstrip().startswith("#"):
                continue
            if HEADING.match(line) or CAPTION.match(line):
                details.append(f"{source.name}:{number} carries document text")
                ok = False
    renderer = (HERE / "render-docs.py").read_text(encoding="utf-8")
    templates = config.path("templates_dir")
    for name in sorted(set(re.findall(r'"((?:chapters|partials)/[\w.-]+\.tmpl)"', renderer))):
        if not (templates / name).is_file():
            details.append(f"template {name} is named by the renderer and missing")
            ok = False
    return ok


def links(details: list[str]) -> bool:
    passed, output = run(PYTHON, script("check-links.py"))
    if not passed:
        details.extend(line for line in output.splitlines() if line.startswith("broken link"))
    return passed


def composition(details: list[str]) -> bool:
    passed, output = run("quarto", "render", cwd=config.DOCS)
    if "Unable to resolve crossref" in output:
        details.append("unresolved cross-reference in the composition")
        return False
    return passed or details.append(output.splitlines()[-1] if output else "quarto render failed")


def artifacts(details: list[str]) -> bool:
    passed, output = run(PYTHON, script("check-rendered.py"))
    if not passed:
        failures = [line for line in output.splitlines() if line.startswith("FAIL ")]
        details.extend(failures[:5])
    return passed


RUNNERS = {
    "smoke": smoke,
    "drift": drift,
    "evidence": evidence,
    "diagram": diagram,
    "purity": purity,
    "links": links,
    "composition": composition,
    "artifacts": artifacts,
}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="docs-gate.py", description=__doc__.splitlines()[0])
    parser.add_argument("--fast", action="store_true",
                        help="stop after purity: skip quarto render and the artifact checks")
    parser.add_argument("--only", choices=STAGES, help="run a single stage")
    args = parser.parse_args(argv)

    selected = [args.only] if args.only else [
        stage for stage in STAGES
        if not (args.fast and stage in ("composition", "artifacts"))
    ]
    failed = []
    for stage in selected:
        details: list[str] = []
        passed = RUNNERS[stage](details)
        print(f"{'OK  ' if passed else 'FAIL'} {stage}")
        for detail in details:
            print(f"     {detail}")
        if not passed:
            failed.append(stage)

    print()
    print(f"gates: {len(selected) - len(failed)}/{len(selected)} passed"
          + (f"; failed: {', '.join(failed)}" if failed else ""))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
