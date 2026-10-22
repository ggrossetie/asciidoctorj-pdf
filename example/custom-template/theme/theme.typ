// A custom template for AsciidoctorJ Typst: a corporate report.
//
// It imports the base template, redefines some of its functions and replaces the document setup (asciidoctor).
// The functions it doesn't redefine (verse, colist, kbd…) come from the base template.
//
// Fonts: the font files of the template directory (fonts/*.ttf) are loaded with the template. The font is selected
// by the family name stored in the file, which can differ from its commercial name: M PLUS Rounded 1c is named
// "Rounded Mplus 1c" (fc-query -f '%{family}\n' fonts/MPLUSRounded1c-Regular.ttf prints it).

#import "/asciidoctor.typ" as base: *

#let primary = rgb("#0b4f6c")
#let accent = rgb("#f2b705")
#let muted = rgb("#6b7c85")
#let tints = (
  note: primary,
  tip: rgb("#3a7d44"),
  important: rgb("#8e44ad"),
  warning: rgb("#d9822b"),
  caution: rgb("#c0392b"),
)

// Inline elements

/// A custom role: [.badge]#text#. The other roles are handled by the base template.
#let phrase(roles, body) = if "badge" in roles {
  box(fill: accent, inset: (x: 4pt), outset: (y: 2.5pt), radius: 3pt, text(size: 0.8em, weight: "bold", body))
} else {
  base.phrase(roles, body)
}

// Blocks

#let block-title(body) = block(sticky: true, below: 0.6em, text(fill: primary, weight: "bold", size: 0.95em, body))

#let admonition(name, caption: none, title: none, body) = {
  let tint = tints.at(name, default: primary)
  block(
    width: 100%, breakable: true, inset: (x: 1em, y: 0.8em), radius: (right: 4pt),
    fill: tint.lighten(92%), stroke: (left: 3pt + tint),
    {
      text(fill: tint, weight: "bold", size: 0.8em, tracking: 0.5pt, upper(if caption == none { name } else { caption }))
      if title != none { text(fill: tint, weight: "bold", size: 0.8em)[ — #title] }
      block(above: 0.6em, body)
    },
  )
}

#let example(title: none, body) = block(
  width: 100%, breakable: true, inset: 1em, radius: 4pt, stroke: (paint: primary.lighten(50%), dash: "dashed"),
  {
    if title != none { block-title(title) }
    body
  },
)

#let sidebar(title: none, body) = block(
  width: 100%, breakable: true, inset: 1.2em, radius: 4pt, fill: accent.lighten(85%),
  {
    if title != none { block(below: 0.8em, text(weight: "bold", fill: primary, size: 1.1em, title)) }
    body
  },
)

#let abstract(title: none, body) = block(
  width: 100%, above: 2.5em, inset: 1.2em, radius: 4pt, fill: primary.lighten(94%),
  {
    text(fill: primary, weight: "bold", size: 0.8em, tracking: 0.5pt, upper(if title == none { "Abstract" } else { title }))
    block(above: 0.7em, body)
  },
)

// Tables: a colored header row (the first row) and horizontal lines only.
#let table-stroke(frame, grid) = (x, y) => (
  bottom: 0.5pt + primary.lighten(60%),
  top: if y == 0 { 1pt + primary },
)

// Document

/// The logo, in color or in shades of gray: Typst has no image filter, so the colors of the SVG source (#RRGGBB) are
/// replaced with their luminance.
#let logo(grayscale: false, ..args) = {
  let svg = read("logo.svg")
  if grayscale {
    svg = svg.replace(regex("#[0-9A-Fa-f]{6}"), match => luma(rgb(match.text)).to-hex())
  }
  image(bytes(svg), format: "svg", ..args)
}

/// The company name with the logo: the end of the name enters the empty bottom left quarter of the logo. The size of
/// the logo follows the size of the text, multiplied by logo-scale.
#let lockup(name, size: 1em, weight: "bold", grayscale: false, logo-scale: 1) = context {
  let label = text(size: size, weight: weight, name)
  let last-word = text(size: size, weight: weight, name.split(" ").last())
  // the empty quarter is about 47% of the width of the logo (a square): about two thirds of the last word enter it
  let logo-size = measure(last-word).width / 0.65 * logo-scale
  let label-height = measure(label).height
  // the slices start at 47% of the width of the logo: leave a gap that follows the size of the text
  let logo-left = measure(label).width - logo-size * 0.47 + label-height * 0.4
  box(width: logo-left + logo-size, height: logo-size, {
    place(top + left, dx: logo-left, logo(grayscale: grayscale, width: logo-size))
    // vertically centered in the bottom half of the logo
    place(top + left, dy: logo-size * 0.74 - label-height / 2, label)
  })
}

#let cover(title: none, subtitle: none, authors: (), revision: none, company: none, classification: none) = page(
  fill: primary, margin: (x: 2.5cm, y: 3cm), header: none, footer: none,
  {
    set text(fill: white)
    if company != none { lockup(company, size: 1.8em) }
    v(1fr)
    line(length: 3cm, stroke: 4pt + accent)
    v(0.5em)
    text(size: 3em, weight: "bold", title)
    if subtitle != none { linebreak(); text(size: 1.6em, fill: accent, subtitle) }
    v(2em)
    if authors.len() > 0 { text(size: 1.2em, authors.map(author => author.name).join(", ")) }
    if revision != none {
      linebreak()
      text(fill: white.darken(20%), ("Version " + revision.number, revision.date).filter(it => it != none).join(" · "))
    }
    v(1fr)
    if classification != none {
      box(stroke: 1pt + accent, inset: (x: 8pt, y: 5pt), radius: 3pt, text(fill: accent, weight: "bold", upper(classification)))
    }
  },
)

#let asciidoctor(
  title: none,
  subtitle: none,
  show-title: true,
  authors: (),
  revision: none,
  doctype: "article",
  lang: "en",
  toc: none,
  front-cover: none,
  back-cover: none,
  attributes: (:),
  body,
) = {
  // custom document attributes
  let company = attributes.at("company", default: none)
  let classification = attributes.at("classification", default: none)

  set document(title: title, author: authors.map(author => author.name))
  set text(font: "Rounded Mplus 1c", size: 10pt, lang: lang)
  set par(justify: true, leading: 0.7em, spacing: 1.2em)
  set page(
    paper: "a4",
    margin: (x: 2.3cm, top: 3cm, bottom: 2.6cm),
    header: context {
      set text(size: 0.85em, fill: muted)
      grid(columns: (auto, 1fr), align: (left + bottom, right + bottom),
        if company != none { lockup(company, weight: "regular", grayscale: true, logo-scale: 0.8) },
        block(below: 0.6em, title),
      )
      v(-0.2em)
      line(length: 100%, stroke: 0.5pt + muted.lighten(50%))
    },
    footer: context {
      set text(size: 0.85em, fill: muted)
      if classification != none { upper(classification) }
      h(1fr)
      counter(page).display("1 / 1", both: true)
    },
  )

  show heading: set text(fill: primary)
  show heading: set block(above: 1.8em, below: 1em, sticky: true)
  show heading.where(level: 1): it => {
    pagebreak(weak: true)
    block(above: 0em, below: 1.4em, sticky: true, {
      text(size: 1.8em, weight: "bold", it)
      v(-0.6em)
      line(length: 2.5cm, stroke: 3pt + accent)
    })
  }
  show heading.where(level: 2): set text(size: 1.25em)
  show link: set text(fill: primary)
  show link: underline.with(stroke: 0.5pt + accent, offset: 2pt)
  show raw: set text(font: "DejaVu Sans Mono")
  show raw.where(block: true): set text(size: 0.82em)
  show raw.where(block: true): it => block(
    width: 100%, inset: 0.9em, radius: (right: 4pt), fill: primary.lighten(95%), stroke: (left: 2pt + primary), it,
  )
  show quote.where(block: true): it => block(inset: (x: 2em), {
    text(size: 1.15em, style: "italic", fill: primary, it.body)
    if it.attribution != none { align(right, text(fill: muted)[— #it.attribution]) }
  })
  show table.cell.where(y: 0): set text(weight: "bold", fill: primary)
  show figure.caption: set text(size: 0.9em, fill: muted)
  show footnote.entry: set text(size: 0.85em)

  // the cover image (front-cover-image), before the title page
  if front-cover != none { base.cover-page(front-cover) }
  if show-title and title != none {
    cover(title: title, subtitle: subtitle, authors: authors, revision: revision, company: company,
      classification: classification)
    counter(page).update(1)
  }
  if toc != none {
    show outline.entry.where(level: 1): set text(weight: "bold", fill: primary)
    show outline.entry.where(level: 1): set block(above: 1.2em)
    outline(title: text(fill: primary, toc.title), depth: toc.levels)
  }
  body
  if back-cover != none { base.cover-page(back-cover) }
}
