# Templates

Document furniture and the Jinja2 templates that produce the generated segments. Copy this directory
next to a `_quarto.yml` and wire the two partials in; nothing here is document-specific.

Verified against Quarto 1.10.19 and Typst 0.15.1. The partials depend on two internal details of that
pair — Typst's `outline(target: …)` and the `kind` values Quarto gives its figures — so re-check them
after an upgrade.

## What an adopter replaces, and what stays

| Keep | Replace |
|---|---|
| `chapters/*.md.tmpl` — the shape of every segment | `labels.yml` — payload titles, response notes, chapter numbers |
| `partials/*.md.tmpl` — macros, evidence marker, banner | the three `.qmd` skeletons — title, parts, includes |
| `page.typ`, `typst-show.typ`, `html-footer.html` | nothing |
| `_quarto.yml` — filter wiring, format, partials | the `render:` list if the document set differs |

A chapter template you do not use costs nothing: `render-docs.py` names the ones it renders, and a
template no renderer names is inert. Delete the segment's entry from the plan rather than the
template, so the next adopter can put it back.

## Wiring

```yaml
format:
  html:
    include-after-body: templates/html-footer.html
  typst:
    toc: true
    toc-depth: 3
    number-sections: false
    template-partials:
      - templates/page.typ
      - templates/typst-show.typ
```

The document declares its identity in the YAML block of its main `.qmd`. Every field is optional and
is skipped on the title page when absent:

```yaml
title: "…"
subtitle: "…"
version: "2.0"
date: "2026-10-17"
status: "Draft for review"
audience: "…"
owner: "…"
skip-lists: true   # a document with neither figures nor tables, or one whose tables are not cited
```

## Generated segments

`chapters/` holds one template per segment, `partials/` the macros and the generator banner, and
`labels.yml` the per-example titles and response notes the chapters share. `render-docs.py` binds them
to the extracted datasets. Template text is the only place a heading, a label or a caption may live,
so edit a template, never a generated file.

The renderer uses `StrictUndefined`: a template that names a missing dataset key fails instead of
printing an empty cell. A table cell holding `|` or `@` must go through the `cell` macro, or it breaks
the table and Quarto's cross-reference resolution with it.

## What the partials do

| File | Renders |
|---|---|
| `typst-show.typ` | The front matter: a title page carrying the YAML fields and nothing else, then the table of contents, then the list of figures, then the list of tables — each on its own page. The body then starts on a fresh page. |
| `page.typ` | A4 geometry and margins, the running header, the `page / total` footer, and a page break in front of every part and chapter. |
| `html-footer.html` | One closing footer line with title, version, status, date and owner, injected through `include-after-body`. It reads the fields with Quarto shortcodes, so it needs no per-document copy. |

Both Typst partials are pandoc-style partial templates: Quarto inlines them into one generated
document, in the order the stock `template.typ` lists them (`page.typ` before `typst-show.typ`). A
partial therefore cannot import a sibling, which is why the title page lives in `typst-show.typ`.

### Header and footer

- The header carries the document title on the left and `part · chapter` on the right, and is drawn
  on **every page except the title page**. On a part divider page the right-hand label is dropped,
  because the body already repeats the part title.
- The footer carries `<page> / <total>` on every page except the title page.
- Both are derived from the heading tree at render time: a page shows the first level-1 or level-2
  heading that starts on it, otherwise the last one before it. Nothing is written per page.

### The two lists

- The list of figures and the list of tables each start their own page and are emitted only when the
  document has at least one of that kind, so a document with figures and no tables gets no empty
  "List of Tables" page.
- `skip-lists: true` omits both lists entirely. It suits a document that has neither, and equally a
  document whose tables are generated per operation and never cited by number: there the list of
  tables would print a second index of the same forty entries.
- `table-density: true` (a key in the document's front matter, not a Quarto option) tightens tables in
  the PDF only: smaller type, tighter leading and cell insets. It is meant for a document whose tables
  carry six columns of prose quoted verbatim from a contract.

## Conventions the template assumes

- `#` is a part or an appendix, `##` a chapter, `###` a section; nothing deeper.
- Headings carry their own number in the text (`## 3 GET /devices`, `### A.1 Payload`). Automatic
  section numbering cannot number chapters continuously across unnumbered parts, cannot letter
  appendices, and disagrees between HTML and PDF, so `number-sections` is off for both formats and
  the composition owns the number.
- A part is written `# Part I — …`; the template treats a `Part `-prefixed level-1 heading as a
  divider page.
- An appendix is written `# Appendix A — …` and puts its entries at `###`, because its entries are
  sections of the appendix rather than chapters: that is what keeps them off a page of their own and
  in the table of contents.
- Cross-references to a chapter are plain text ("chapter 12"), because the number lives in the
  heading text and Quarto's `@sec-` numbering would compute a different one. Renumbering a chapter
  therefore means updating the chapters that name it.
- Figures are referenced as `![Caption](path){#fig-name}` and tables as a caption line
  `: Caption {#tbl-name}`; both appear in the corresponding list.
- Quarto emits figures with `kind: "quarto-float-fig"` and tables with `kind: "quarto-float-tbl"`,
  which is what the two lists select on.
