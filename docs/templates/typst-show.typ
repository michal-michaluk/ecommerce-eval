// Title page, front matter and document shell.
//
// Replaces Quarto's default `typst-show.typ` partial. It wraps the document body in the article
// function and places, in this order: the title page, the table of contents, the list of figures
// and the list of tables, each on its own page. The body then starts on a fresh page.
//
// Everything is defined here rather than in a second file: Quarto inlines the partials it is given
// into one document, so a partial cannot import a sibling partial.
//
// The outlines are emitted at the top level of the document on purpose: wrapping them in a block,
// as the stock partial does, makes Typst reject the page break that `page.typ` puts in front of a
// part heading.

#let title-page(
  title: none,
  subtitle: none,
  version: none,
  date: none,
  status: none,
  audience: none,
  owner: none,
) = {
  v(1fr)
  set align(center)
  set par(justify: false)
  block(width: 100%)[
    #text(size: 1.9em, weight: "bold", hyphenate: false)[#title]
    #if subtitle != none {
      v(0.6em)
      text(size: 1.15em, fill: luma(70), hyphenate: false)[#subtitle]
    }
  ]

  v(2.2em)
  align(center, line(length: 45%, stroke: 0.6pt + luma(160)))
  v(1.6em)

  set align(left)
  let rows = (
    ("Version", version),
    ("Date", date),
    ("Status", status),
    ("Audience", audience),
    ("Owner", owner),
  ).filter(row => row.at(1) != none)
  if rows.len() > 0 {
    align(center,
      grid(
        columns: (auto, auto),
        align: (right, left),
        column-gutter: 1.2em,
        row-gutter: 0.45em,
        ..rows.map(row => ([#text(fill: luma(90))[#row.at(0)]], row.at(1))).flatten(),
      )
    )
  }
  v(1fr)
}

#show: doc => {
  title-page(
$if(title)$
    title: [$title$],
$endif$
$if(subtitle)$
    subtitle: [$subtitle$],
$endif$
$if(version)$
    version: [$version$],
$endif$
$if(date)$
    date: [$date$],
$endif$
$if(status)$
    status: [$status$],
$endif$
$if(audience)$
    audience: [$audience$],
$endif$
$if(owner)$
    owner: [$owner$],
$endif$
  )

  pagebreak()
  outline(title: [Table of Contents], depth: $toc-depth$, indent: 1.2em)
$if(skip-lists)$
  pagebreak()
$else$
  pagebreak()
  // One line per list, and no list at all when the document has no such figure. An empty
  // "List of Tables" page would be a defect, and only a query can tell.
  context {
    let figures = query(figure.where(kind: "quarto-float-fig"))
    let tables = query(figure.where(kind: "quarto-float-tbl"))
    if figures.len() > 0 {
      outline(title: [List of Figures], target: figure.where(kind: "quarto-float-fig"), depth: 1)
    }
    if tables.len() > 0 {
      pagebreak()
      outline(title: [List of Tables], target: figure.where(kind: "quarto-float-tbl"), depth: 1)
    }
  }
  pagebreak()
$endif$

  article(
$if(title)$
    title: none,
$endif$
$if(subtitle)$
    subtitle: none,
$endif$
    doc,
  )
}
