# Documentation pipeline scripts

Two stages, both deterministic and offline: extract facts from source, then bind them to templates.
No script contains a heading, a label, a caption or a sentence — document text comes only from source
artefacts, from `templates/`, or from the authored segments.

Everything repository-specific is in `config.json`. Edit that file, not a script.

## Pipeline

| # | Command | Reads | Writes |
|---|---|---|---|
| 1 | `python3 docs/scripts/sync-openapi.py` | `openapi.source` | `openapi.target` |
| 2 | `python3 docs/scripts/extract-data.py` | `sources.*`, `openapi.target` | `data_dir/*.json` |
| 3 | `python3 docs/scripts/render-docs.py` | `data_dir/`, `templates_dir/` | `chapters_dir/*.md` |
| 4 | `python3 docs/scripts/render-diagrams.py` | `diagram_source_dir/*.mmd` | `diagrams_dir/*.svg` |
| 5 | `python3 docs/scripts/verify-evidence.py` | `authored_dirs`, `data_dir/` | — |
| 6 | `python3 docs/scripts/check-links.py` | `authored_dirs`, `chapters_dir` | — |
| 7 | `cd docs && quarto render` | the `.qmd` documents and every included segment | `_build/*.html`, `_build/*.pdf` |
| 8 | `python3 docs/scripts/check-rendered.py` | `_build/*.pdf`, `_build/*.html` | — |

`extract-data.py` runs one module per dataset from `extractors/` and stores sorted JSON with
`evidence` lists of repository-relative `path:line`. `render-docs.py` renders one template per
segment from `templates/chapters/` through the macros in `templates/partials/`; the label lookups in
`templates/labels.yml` name a payload by the test that asserts it. Segment file names are the ones
the `.qmd` documents include, so a new segment needs a new include.

## Datasets and their extractors

| Dataset | Module | Reusable |
|---|---|---|
| `openapi` | `extractors/openapi.py` | starting point |
| `persistence` | `extractors/persistence.py` | starting point |
| `payloads` | `extractors/payloads.py` | starting point |
| `examples` | `extractors/examples.py` | starting point |
| `events` | `extractors/events.py` | starting point |
| `deployment` | `extractors/deployment.py` | starting point |
| `observability` | `extractors/observability.py` | starting point |
| `security` | `extractors/security.py` | starting point |
| `adr` | `extractors/adr.py` | as it stands |
| `git_history` | `extractors/git_history.py` | as it stands |

A *starting point* reads one stack's artefacts — a Liquibase changelog, a kustomize tree, a Spring
filter chain — and its module docstring names what to re-map. `common.py`, `git_history.py` and
`adr.py` are stack-independent; so are the eight scripts that drive them — `config.py`,
`sync-openapi.py`, `extract-data.py`, `render-docs.py`, `render-diagrams.py`,
`verify-evidence.py`, `check-links.py`, `check-rendered.py` and `docs-gate.py`.

Stage 1 is skipped when `openapi.source` is empty, which is the case when the build produces no
OpenAPI document. `extractors/openapi.py` then reads the contract the repository does carry — the
`@RestController` mappings and `sources.e2e_scenario` — and `extract-data.py` writes
`data_dir/openapi.json` itself; an operation the scenario never calls is recorded as not exercised.
An operation's `summary` is the Javadoc that documents it — the handler's first sentence, or the
class's when the class maps one route — and its handler name otherwise, with `summary_source` saying
which; extraction refuses a route whose summary is empty or shared with another route.

## Gates

Every writing script has a non-mutating mode; two runs are byte-identical.

```sh
python3 docs/scripts/extract-data.py --smoke    # every extractor alone, 20s limit, no writes
python3 docs/scripts/extract-data.py --check    # smoke first, then datasets and counts
python3 docs/scripts/sync-openapi.py --check
python3 docs/scripts/render-docs.py --check     # segments current, no orphan segment
python3 docs/scripts/render-diagrams.py --check
python3 docs/scripts/verify-evidence.py         # every marker resolves to a real line
python3 docs/scripts/check-links.py             # every relative link and image resolves
cd docs && quarto render 2>&1 | tee /tmp/quarto.log
! grep -q "Unable to resolve crossref" /tmp/quarto.log
python3 docs/scripts/check-rendered.py          # asserts on the HTML and the PDF
```

`docs-gate.py` runs the chain in order and reports one line per stage:

```sh
python3 docs/scripts/docs-gate.py          # smoke, drift, evidence, diagram, purity, links, composition, artifacts
python3 docs/scripts/docs-gate.py --fast   # stop after purity
python3 docs/scripts/docs-gate.py --only drift
```

`extract-data.py --check` fails on the first stale dataset and on any count drift; the counts it
compares are `expected_counts` in `config.json`. Leave that object empty to begin with, fill it once
the first extraction is reviewed, and never relax it to make a gate pass.

## Provenance

Templates emit one invisible `<!-- evidence: path:line, … -->` comment per claim; the comments stay
in the sources and `strip-evidence.lua` removes them at render time, so a reader never sees a path or
a line number. `verify-evidence.py` rejects a missing file, an out-of-range line and a blank or
`package`/`import` line, and exits 1.

## Rules

- A script never emits document text; labels and empty states belong to the templates.
- Nothing here touches the network, a database or the application; `--print`/`--list` write nothing.
- Only `render-diagrams.py` needs a subprocess (headless Chrome) and `git_history.py` needs `git`;
  everything else reads files.
