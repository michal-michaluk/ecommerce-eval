// Page geometry, running header and footer, heading numbering.
//
// Replaces Quarto's default `page.typ` partial. It owns everything that is page furniture:
//
//   * paper, margins and the running header (document name, part or chapter of the page), rendered
//     on every page except the title page;
//   * the footer, rendered as `<page> / <total>`, suppressed on the title page;
//   * a page break in front of every part and every chapter, so a part and a chapter each start on
//     a fresh page and the running header always names the chapter the page belongs to.
//
// Headings are not numbered by the template: the number is part of the heading text (`## 3 GET
// /devices`). Automatic section numbering cannot express a document whose chapters are numbered
// continuously across unnumbered parts and whose appendices are lettered, and it disagrees between
// HTML and PDF, so the composition owns the number instead.

#set page(
  paper: "a4",
  margin: (x: 2.1cm, top: 2.6cm, bottom: 2.0cm),
  numbering: none,
  header: context {
    let current = counter(page).get().first()
    if current > 1 {
      // Outline titles are headings too, but flagged `outlined: false`.
      let heads = query(heading).filter(head => head.outlined)
      let here = heads.filter(head => head.location().page() == current)
      let before = heads.filter(head => head.location().page() < current)
      let nearest(level) = {
        let on-page = here.filter(head => head.level == level)
        if on-page.len() > 0 { on-page.first() } else { before.filter(head => head.level == level).at(-1, default: none) }
      }
      let part = nearest(1)
      let found = nearest(2)
      // An appendix has no chapters; a chapter from before the part that opened the page is stale.
      let start = if part != none { part.location().page() } else { 0 }
      let chapter = if found != none and found.location().page() >= start { found } else { none }
      let label = if part != none and chapter != none {
        [#part.body#h(0.6em)\u{b7}#h(0.6em)#chapter.body]
      } else if chapter != none {
        chapter.body
      } else if part != none {
        part.body
      } else {
        none
      }
      // A part divider page repeats its own title in the body, so the running head keeps the
      // document name and drops the right-hand label.
      let divider = part != none and chapter == none and part.location().page() == current and content-to-string(part.body).starts-with("Part ")
      set text(size: 7.5pt, fill: luma(120))
      set par(justify: false)
      grid(
        columns: (1fr, auto),
        align: (left, right),
        [$title$],
        if label != none and not divider { label } else { [] },
      )
      v(-0.6em)
      line(length: 100%, stroke: 0.4pt + luma(200))
    }
  },
  footer: context {
    let current = counter(page).get().first()
    if current > 1 {
      set align(center)
      set text(size: 8pt, fill: luma(120))
      [#current#h(0.35em)/#h(0.35em)#counter(page).final().first()]
    }
  },
)

$if(table-density)$
// A document that asks for `table-density: true` in its front matter carries wide field tables: six
// columns of prose taken verbatim from a contract. Tighten tables for that document only.
#show table: set text(size: 7pt)
#show table: set par(leading: 0.4em)
#show table: set table(inset: (x: 3pt, y: 1.4pt), stroke: 0.3pt + luma(190))
#show table: set block(spacing: 0.9em)
#show table.cell: set par(justify: false)
$endif$

// A table longer than a page has to break; Typst figures are unbreakable unless told otherwise.
#show figure.where(kind: "quarto-float-tbl"): set block(breakable: true)

#show heading.where(level: 1): it => {
  pagebreak(weak: true)
  // A part opens a divider page of its own; the drop keeps it from reading as a stray line.
  pad(top: 4.5cm, it)
}

#show heading.where(level: 2): it => {
  pagebreak(weak: true)
  it
}
