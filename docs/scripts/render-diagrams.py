#!/usr/bin/env python3
"""Pre-render the Mermaid diagram sources of the system documentation to SVG.

Quarto 1.10.19 in this installation emits an inline ```` ```mermaid ```` block as
``<pre class="mermaid">`` without a Mermaid loader, and for Typst it emits the source as a
plain code block, so neither HTML nor PDF shows a diagram. This script removes the
dependency on a client-side runtime: every ``docs/diagrams/*.mmd`` source is rendered to
``docs/generated/diagrams/<name>.svg`` at build time and the manual segments reference the
resulting image with a normal markdown image.

Rendering uses Google Chrome in headless mode on a temporary HTML page that loads Mermaid
from the bundle shipped with the Quarto installation. Nothing is downloaded: the bundle is
resolved from the Quarto binary that is on ``PATH``, and no network request is made.

Prerequisites: ``quarto`` on ``PATH`` and a Chrome/Chromium binary at ``CHROME`` (override the
path with the ``CHROME`` environment variable on a machine where Chrome lives elsewhere).

Determinism
-----------
Mermaid stamps a generated element id and animation rules into the emitted SVG. Both are
normalised away: the root ``id`` and every ``mermaid-<digits>`` reference become
``diagram-<name>``, the ``@keyframes`` blocks and the animation rules are dropped, repeated
identical selectors are collapsed, and the root ``width``/``height`` are derived from the
``viewBox`` instead of the rendered ``max-width`` style. Two runs therefore produce
byte-identical files.

Modes
-----
(default)        write every ``docs/generated/diagrams/<name>.svg``
--check          compare on-disk SVGs with freshly rendered ones, never write, exit 1 on drift
--print <name>   render one diagram and write the SVG to stdout

The script never writes outside ``docs/generated/diagrams/``.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import xml.dom.minidom
import xml.parsers.expat
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import config  # noqa: E402

REPO = config.root()
SOURCE_DIR = config.path("diagram_source_dir")
OUTPUT_DIR = config.path("diagrams_dir")
CHROME = Path(
    os.environ.get("CHROME") or config.DATA.get("chrome")
    or "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
)
MERMAID_BUNDLE = Path("share/formats/html/mermaid/mermaid.min.js")
RENDER_TIMEOUT = 60

PAGE = """<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>mermaid</title></head>
<body>
<div id="canvas">RENDER-PENDING</div>
<script src="{bundle}"></script>
<script>
// Mermaid jitters the control points of parallel edges with Math.random(), so the same source
// would render a different SVG on every run; a seeded generator makes the output reproducible.
let seed = 0x9e3779b9;
Math.random = function () {{
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}};
const source = {source};
mermaid.initialize({{
  startOnLoad: false,
  theme: "default",
  securityLevel: "loose",
  htmlLabels: false,
  flowchart: {{ htmlLabels: false, useMaxWidth: false, nodeSpacing: 25, rankSpacing: 30, padding: 8, wrappingWidth: 320 }},
  sequence: {{
    useMaxWidth: false,
    mirrorActors: false,
    actorMargin: 30,
    boxMargin: 4,
    messageMargin: 25,
    noteMargin: 5,
    activationWidth: 8,
    diagramMarginX: 5,
    diagramMarginY: 5,
  }},
  c4: {{ useMaxWidth: false }},
}});
mermaid
  .render("{render_id}", source)
  .then(function (result) {{ document.getElementById("canvas").innerHTML = result.svg; }})
  .catch(function (error) {{ document.getElementById("canvas").textContent = "RENDER-FAILED " + error; }});
</script>
</body>
</html>
"""
CANVAS_RE = re.compile(r'<div id="canvas">(.*)</div>', re.S)


def mermaid_bundle() -> Path:
    """Resolve the Mermaid bundle that ships with the Quarto installation on PATH."""
    quarto = shutil.which("quarto")
    if quarto is None:
        raise SystemExit("quarto is not on PATH; cannot resolve the bundled mermaid.min.js")
    root = Path(quarto).resolve().parent.parent
    bundle = root / MERMAID_BUNDLE
    if not bundle.is_file():
        raise SystemExit(
            f"mermaid bundle not found at {bundle}; expected it under the Quarto installation "
            f"root {root} resolved from {quarto}"
        )
    return bundle


PLACEHOLDER = "Syntax error in text"


def assert_rendered(name: str, svg: str) -> None:
    """A Mermaid parser failure still produces an <svg>; refuse to write that placeholder."""
    if PLACEHOLDER in svg:
        raise SystemExit(f"{name}: Mermaid could not parse the source — {PLACEHOLDER}")
    if 'class="node' not in svg:
        raise SystemExit(f"{name}: rendered SVG carries no node, so the diagram is empty")


def sources() -> list[Path]:
    return sorted(SOURCE_DIR.glob("*.mmd"))


def output_path(name: str) -> Path:
    return OUTPUT_DIR / f"{name}.svg"


def svg_element(document: str) -> str:
    """Return the first complete <svg> element of the dumped DOM, honouring nesting."""
    start = document.find("<svg")
    if start < 0:
        return ""
    depth = 0
    cursor = start
    while cursor < len(document):
        open_at = document.find("<svg", cursor)
        close_at = document.find("</svg>", cursor)
        if close_at < 0:
            return ""
        if 0 <= open_at < close_at:
            depth += 1
            cursor = open_at + 4
            continue
        depth -= 1
        cursor = close_at + 6
        if depth == 0:
            return document[start:cursor]
    return ""


def normalise(svg: str, name: str) -> str:
    """Strip the generated id and the animation artifacts, pin the intrinsic size."""
    stable = f"diagram-{name}"
    svg = re.sub(r"@keyframes\s+[\w-]+\s*\{(?:[^{}]|\{[^{}]*\})*\}\s*", "", svg)
    svg = re.sub(r"\.edge-animation-(?:slow|fast)\s*\{[^}]*\}\s*", "", svg)
    svg = re.sub(r"mermaid-\d+", stable, svg)
    selector = re.escape(f"#{stable}")
    svg = re.sub(rf"(?:{selector} ){{2,}}", f"#{stable} ", svg)

    opening_end = svg.find(">")
    opening = svg[: opening_end + 1]
    if "xlink:" in svg and "xmlns:xlink" not in opening:
        opening = opening.replace("<svg", '<svg xmlns:xlink="http://www.w3.org/1999/xlink"', 1)
    opening = re.sub(r'\sid="[^"]*"', "", opening)
    opening = re.sub(r'\sstyle="[^"]*"', "", opening)
    box = re.search(r'viewBox="([-\d.]+) ([-\d.]+) ([\d.]+) ([\d.]+)"', opening)
    if box:
        width, height = box.group(3), box.group(4)
        opening = re.sub(r'\swidth="[^"]*"', "", opening)
        opening = re.sub(r'\sheight="[^"]*"', "", opening)
        opening = opening.replace("<svg", f'<svg id="{stable}" width="{width}" height="{height}"', 1)
    else:
        opening = opening.replace("<svg", f'<svg id="{stable}"', 1)
    svg = opening + svg[opening_end + 1 :]
    return svg.rstrip() + "\n"


def render(source: Path, bundle: Path) -> str:
    text = source.read_text(encoding="utf-8")
    literal = json.dumps(text).replace("</", "<\\/")
    page = PAGE.format(bundle=bundle.as_uri(), source=literal, render_id=f"diagram-{source.stem}")
    with tempfile.TemporaryDirectory(prefix="mermaid-") as workdir:
        page_path = Path(workdir) / "page.html"
        page_path.write_text(page, encoding="utf-8")
        completed = subprocess.run(
            [
                str(CHROME),
                "--headless",
                "--disable-gpu",
                "--no-sandbox",
                "--virtual-time-budget=8000",
                "--dump-dom",
                page_path.as_uri(),
            ],
            capture_output=True,
            text=True,
            timeout=RENDER_TIMEOUT,
        )
    canvas = CANVAS_RE.search(completed.stdout)
    if canvas is None:
        raise SystemExit(f"{source.name}: chrome returned no render canvas ({completed.stderr.strip()[-400:]})")
    svg = svg_element(canvas.group(1))
    if not svg:
        raise SystemExit(f"{source.name}: mermaid did not render the diagram ({(canvas.group(1).strip())[:400]})")
    normalised = normalise(svg, source.stem)
    try:
        xml.dom.minidom.parseString(normalised)
    except xml.parsers.expat.ExpatError as error:
        raise SystemExit(f"{source.name}: rendered svg is not well-formed xml ({error})")
    return normalised


def check_chrome() -> None:
    if not CHROME.is_file():
        raise SystemExit(f"Google Chrome not found at {CHROME}; it is required to render diagrams")


def run_write() -> int:
    check_chrome()
    bundle = mermaid_bundle()
    files = sources()
    if not files:
        raise SystemExit(f"no mermaid sources under {SOURCE_DIR}")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for source in files:
        target = output_path(source.stem)
        if target.resolve().parent != OUTPUT_DIR.resolve():
            raise SystemExit(f"refusing to write outside {OUTPUT_DIR}: {target}")
        svg = render(source, bundle)
        assert_rendered(source.stem, svg)
        target.write_text(svg, encoding="utf-8")
        print(f"wrote {target.relative_to(REPO).as_posix()}")
    return 0


def run_check() -> int:
    check_chrome()
    bundle = mermaid_bundle()
    expected_names = {source.stem for source in sources()}
    problems = []
    for source in sources():
        target = output_path(source.stem)
        rendered = render(source, bundle)
        actual = target.read_text(encoding="utf-8") if target.exists() else None
        if actual is None:
            problems.append(f"{source.stem}: {target.relative_to(REPO).as_posix()} is missing")
        elif actual != rendered:
            problems.append(f"{source.stem}: {target.relative_to(REPO).as_posix()} is stale")
    for existing in sorted(OUTPUT_DIR.glob("*.svg")):
        if existing.stem not in expected_names:
            problems.append(f"{existing.stem}: {existing.relative_to(REPO).as_posix()} has no source")
    if problems:
        print("rendered diagrams are out of date:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        print("run: python3 docs/scripts/render-diagrams.py", file=sys.stderr)
        return 1
    print(f"all {len(expected_names)} rendered diagrams match their sources")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="render-diagrams.py",
        description="Pre-render docs/diagrams/*.mmd to docs/generated/diagrams/*.svg using "
        "Google Chrome headless and the Mermaid bundle shipped with Quarto. Offline and "
        "deterministic; writes only under docs/generated/diagrams/.",
    )
    parser.add_argument("--check", action="store_true", help="verify the rendered SVGs are current without writing")
    parser.add_argument("--print", dest="print_name", metavar="NAME", help="render one diagram to stdout")
    args = parser.parse_args(argv)

    if args.print_name:
        check_chrome()
        source = SOURCE_DIR / f"{args.print_name}.mmd"
        if not source.is_file():
            raise SystemExit(f"no diagram source {source.relative_to(REPO).as_posix()}")
        sys.stdout.write(render(source, mermaid_bundle()))
        return 0
    if args.check:
        return run_check()
    return run_write()


if __name__ == "__main__":
    raise SystemExit(main())
