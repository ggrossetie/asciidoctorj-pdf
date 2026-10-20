// Base template of AsciidoctorJ PDF: the default rendering of the AsciiDoc elements.
//
// The converted document imports this module, then the template given with the pdf-template attribute, if any. A
// template can import this module (#import "/asciidoctor.typ": *) and redefine any of these functions, or style the
// native Typst elements (heading, list, table, raw, quote, link…) with show rules.

#let colors = (
  text: rgb("#333333"),
  heading: rgb("#1a1a1a"),
  link: rgb("#2156a5"),
  muted: rgb("#7a7a7a"),
  border: rgb("#dddddd"),
  code-background: rgb("#f7f7f8"),
  note: rgb("#19407c"),
  tip: rgb("#111111"),
  important: rgb("#bf0000"),
  warning: rgb("#bf6900"),
  caution: rgb("#bf3400"),
)

/// The default fonts (embedded in Typst). A template changes them with the fonts argument of asciidoctor.
#let fonts = (
  body: ("Libertinus Serif",),
  heading: ("Libertinus Serif",),
  mono: ("DejaVu Sans Mono",),
)

/// The fonts of the document: the default fonts, overridden by the fonts argument of asciidoctor.
#let document-fonts = state("asciidoctor-fonts", fonts)
#let default-fonts = fonts

/// Whether the parts and the chapters start on a recto (odd) page: true when media is prepress and the doctype is book.
#let facing-pages = state("asciidoctor-facing-pages", false)
/// Whether the next facing page break goes to a recto page: nonfacing() makes it go to the next page, once.
#let next-facing = state("asciidoctor-next-facing", true)

/// Makes the next part or chapter ([%nonfacing]) start on the next page rather than on the next recto page.
#let nonfacing() = next-facing.update(false)

/// The page break before a part, a chapter or the body of a book: to the next recto page when the pages are facing.
#let facing-pagebreak() = {
  context pagebreak(weak: true, to: if facing-pages.get() and next-facing.get() { "odd" })
  next-facing.update(true)
}

// Inline elements

/// The URI displayed after the text of a link in a printed document (media=print or prepress, or show-link-uri);
/// without its scheme when hide-uri-scheme is set. The line can break after the /, ?, & and # characters.
#let link-uri(uri) = {
  let (scheme, address) = if uri.contains("://") {
    let end = uri.position("://") + 3
    (uri.slice(0, end), uri.slice(end))
  } else {
    ("", uri)
  }
  let chars = address.clusters()
  // NOTE at least two characters after a break
  let breakable = chars.enumerate().map(((i, char)) => {
    if char in ("/", "?", "&", "#") and i < chars.len() - 2 { char + "\u{200b}" } else { char }
  }).sum(default: "")
  [ \[#text(size: 0.85em, hyphenate: false, scheme + breakable)\]]
}

/// Monospaced text (`text`).
#let mono(body) = context text(font: document-fonts.get().mono, size: 0.88em, body)

/// A phrase with roles ([.role]#text#).
#let phrase(roles, body) = {
  let styled = body
  for role in roles {
    styled = if role == "underline" { underline(styled) }
    else if role == "line-through" { strike(styled) }
    else if role == "big" { text(size: 1.2em, styled) }
    else if role == "small" { text(size: 0.8em, styled) }
    else if role in ("red", "green", "blue", "yellow", "orange", "purple", "gray", "black", "white", "maroon",
      "navy", "olive", "teal", "aqua", "fuchsia", "lime", "silver") { text(fill: eval(role), styled) }
    else { styled }
  }
  styled
}

/// A keyboard shortcut (kbd:[Ctrl+T]): an array of keys.
#let kbd(keys) = keys.map(key => box(
  inset: (x: 3pt), outset: (y: 2pt), radius: 2pt, stroke: 0.5pt + colors.border, fill: colors.code-background,
  mono(key),
)).join([+])

/// A button (btn:[Save]).
#let btn(body) = [\[#strong(body)\]]

/// A menu selection (menu:File[Save]): an array of the menu, the submenus and the item.
#let menu(items) = items.map(strong).join([~›~])

/// A callout number in a verbatim block or in a callout list.
#let callout(number) = box(
  baseline: 15%, circle(radius: 0.55em, fill: colors.text, inset: 0pt,
    align(center + horizon, context text(fill: white, size: 0.75em, weight: "bold",
      font: document-fonts.get().mono, str(number)))),
)

/// An icon (icon:name[]). There is no icon font: the name is displayed.
#let icon(name) = [\[#name\]]

/// The marker of a checklist item: checked is none for an item without a checkbox.
#let checkbox(checked) = if checked == none { [•] } else if checked { [☑] } else { [☐] }

/// A STEM expression: the source is displayed as is (Typst has no TeX or AsciiMath parser).
#let stem(notation, source, block: false) = raw(source, block: block)

// Block elements

/// The title of a block (.Title).
#let block-title(body) = block(sticky: true, below: 0.65em, text(style: "italic", fill: colors.heading, body))

/// A paragraph or a block with roles.
#let role-block(roles, body) = if "lead" in roles { text(size: 1.15em, body) } else { phrase(roles, body) }

/// An admonition (NOTE, TIP, IMPORTANT, WARNING, CAUTION). The caption is the localized label.
#let admonition(name, caption: none, title: none, body) = block(
  width: 100%, breakable: true, above: 1.2em, below: 1.2em,
  grid(
    columns: (auto, 1fr),
    column-gutter: 0.9em,
    align: (center + horizon, left),
    text(weight: "bold", size: 0.85em, fill: colors.at(name, default: colors.text),
      upper(if caption == none { name } else { caption })),
    block(inset: (left: 0.9em, y: 0.3em), stroke: (left: 0.5pt + colors.border), width: 100%, {
      if title != none { block-title(title) }
      body
    }),
  ),
)

/// An example block (====).
#let example(title: none, body) = {
  if title != none { block-title(title) }
  block(width: 100%, breakable: true, inset: 1em, radius: 2pt, stroke: 0.5pt + colors.border, body)
}

/// A sidebar block (****).
#let sidebar(title: none, body) = block(
  width: 100%, breakable: true, inset: 1em, radius: 2pt, fill: rgb("#f3f3f8"), stroke: 0.5pt + colors.border, {
    if title != none { align(center, text(weight: "bold", size: 1.1em, title)) }
    body
  },
)

/// A verse block ([verse]): the line breaks are preserved.
#let verse(attribution: none, body) = block(inset: (left: 1.5em), {
  body
  if attribution != none { block(above: 0.8em, align(right, text(size: 0.9em, fill: colors.muted, [— #attribution]))) }
})

/// A listing or literal block whose content has inline formatting (e.g., subs=+quotes).
#let verbatim(context-name, body) = block(
  width: 100%, inset: 0.8em, radius: 3pt, fill: colors.code-background, stroke: 0.5pt + colors.border,
  mono(body),
)

/// An image block (image::target[]). The image is none when its file is not found.
#let image-block(image, alt: none, caption: none, align: none, link: none) = {
  let body = if image == none { text(fill: colors.muted, [\[#alt\]]) } else { image }
  if link != none { body = std.link(link, body) }
  let alignment = if align == "center" { center } else if align == "right" { right } else { left }
  figure(
    std.align(alignment, body),
    caption: if caption != none { figure.caption(position: bottom, caption) },
    kind: std.image, numbering: none, supplement: none, outlined: false,
  )
}

/// The table block that encloses a table.
#let table-block(caption: none, width: 100%, align: none, table) = figure(
  block(width: width, table),
  caption: if caption != none { figure.caption(position: top, caption) },
  kind: std.table, numbering: none, supplement: none, outlined: false,
)

/// The stroke of a table, from the frame (all, topbot, ends, sides, none) and grid (all, rows, cols, none) attributes.
#let table-stroke(frame, grid) = {
  let line = 0.5pt + colors.border
  (x, y) => (
    left: if (frame == "all" or frame == "sides") and x == 0 { line } else if grid in ("all", "cols") and x > 0 { line },
    top: if (frame == "all" or frame in ("topbot", "ends")) and y == 0 { line } else if grid in ("all", "rows") and y > 0 { line },
    right: if frame == "all" or frame == "sides" { line },
    bottom: if frame == "all" or frame in ("topbot", "ends") { line },
  )
}

/// The fill of the rows of a table, from the stripes attribute (even, odd, all, hover, none).
#let table-fill(stripes) = (x, y) => {
  if stripes == "all" or (stripes == "even" and calc.even(y)) or (stripes == "odd" and calc.odd(y)) {
    rgb("#f8f8f7")
  }
}

/// A callout list: an array of items.
#let colist(items) = grid(
  columns: (auto, 1fr), column-gutter: 0.6em, row-gutter: 0.6em,
  ..items.enumerate(start: 1).map(((number, item)) => (callout(number), item)).flatten(),
)

/// A question and answer list ([qanda]): an array of (question, answer) pairs.
#let qanda(items) = enum(..items.map(((question, answer)) => [#emph(question) \ #answer]))

/// A horizontal description list ([horizontal]): an array of (term, description) pairs.
#let hdlist(items) = grid(
  columns: (auto, 1fr), column-gutter: 1.5em, row-gutter: 0.8em,
  ..items.map(((term, description)) => (strong(term), description)).flatten(),
)

/// An abstract ([abstract]).
#let abstract(title: none, body) = block(
  width: 100%, above: 2.5em, below: 2.5em, inset: (x: 2em, y: 1.2em), stroke: (y: 0.5pt + colors.border),
  {
    if title != none { align(center, block-title(title)) }
    // NOTE upright, so that the monospaced text doesn't clash with the text
    text(size: 1.05em, fill: colors.heading, body)
  },
)

/// A thematic break (''').
#let thematic-break() = align(center, line(length: 50%, stroke: 0.5pt + colors.border))

/// A part of a book (= Part).
#let part(title) = {
  facing-pagebreak()
  v(30%)
  align(center, text(size: 2.4em, weight: "bold", fill: colors.heading, title))
  pagebreak(weak: true)
}

/// The table of contents.
#let toc-block(title: [Table of Contents], levels: 2) = {
  show outline.entry.where(level: 1): set block(above: 0.9em)
  outline(title: title, depth: levels)
}

/// The title page of a book, or the title of an article.
#let title-page(title: none, subtitle: none, authors: (), revision: none, doctype: "article") = {
  let details = {
    if authors.len() > 0 { authors.map(author => author.name).join(", ") }
    if revision != none {
      let parts = ()
      if revision.number != none { parts.push("Version " + revision.number) }
      if revision.date != none { parts.push(revision.date) }
      linebreak()
      text(fill: colors.muted, parts.join(", "))
    }
  }
  if doctype == "book" {
    v(35%)
    align(right, {
      text(size: 2.4em, fill: colors.heading, title)
      if subtitle != none { linebreak(); text(size: 1.4em, fill: colors.muted, subtitle) }
      v(2em)
      details
    })
    pagebreak()
  } else {
    align(center, block(below: 2em, {
      text(size: 2em, fill: colors.heading, title)
      if subtitle != none { linebreak(); text(size: 1.3em, fill: colors.muted, subtitle) }
      v(0.5em)
      details
    }))
  }
}

/// The document: sets up the page and the styles, then displays the title, the table of contents and the body.
///
/// - fonts: the fonts that replace the default ones, e.g. (body: "Noto Serif", heading: "Noto Sans"); the fonts must
///   be embedded in Typst, in the template directory or in pdf-fontsdir.
/// - margin: the margins of the pages, a length or a dictionary like the margin of page.
/// - margin-inner, margin-outer: when media is prepress, the side margins of the pages, on the side of the binding
///   (left on a recto page, right on a verso page) and on the other side; none keeps the side margin of margin.
/// - attributes: the document attributes (a dictionary of strings), for instance to read custom attributes.
///
/// The media attribute adapts the document to its medium: screen (the default), print (links in the text color) or
/// prepress (print, plus mirrored margins and, for a book, the title page, the table of contents, the body, the parts
/// and the chapters start on a recto page).
#let asciidoctor(
  title: none,
  subtitle: none,
  show-title: true,
  authors: (),
  revision: none,
  doctype: "article",
  lang: "en",
  toc: none,
  fonts: (:),
  margin: (x: 2cm, top: 2cm, bottom: 2.5cm),
  margin-inner: 2.25cm,
  margin-outer: 1.75cm,
  attributes: (:),
  body,
) = {
  let fonts = default-fonts + fonts
  document-fonts.update(fonts)
  let media = attributes.at("media", default: "screen")
  let facing = media == "prepress" and doctype == "book"
  facing-pages.update(facing)
  // mirrored margins: the left and right margins become the inside and outside margins
  let margin = if media == "prepress" {
    let margin = if type(margin) == dictionary { margin } else { (rest: margin) }
    let side(name) = margin.at(name, default: margin.at("x", default: margin.at("rest", default: auto)))
    let inside = if margin-inner != none { margin-inner } else { side("left") }
    let outside = if margin-outer != none { margin-outer } else { side("right") }
    let margin = margin.pairs().filter(((key, _)) => key not in ("left", "right", "x")).to-dict()
    margin + (inside: inside, outside: outside)
  } else {
    margin
  }
  set document(
    title: title,
    author: authors.map(author => author.name),
  )
  set page(
    paper: lower(attributes.at("pdf-page-size", default: "a4")),
    margin: margin,
    numbering: "1",
  )
  set text(font: fonts.body, size: 10.5pt, fill: colors.text, lang: lang)
  set par(justify: true, leading: 0.65em, spacing: 1.1em)

  show heading: set text(font: fonts.heading, fill: colors.heading, weight: "regular")
  show heading: set block(above: 1.6em, below: 0.9em, sticky: true)
  show heading.where(level: 1): set text(size: if doctype == "book" { 2em } else { 1.6em })
  show heading.where(level: 2): set text(size: 1.4em)
  show heading.where(level: 3): set text(size: 1.2em)
  show heading.where(level: 4): set text(size: 1.1em)
  show heading.where(level: 1): it => if doctype == "book" { facing-pagebreak(); it } else { it }

  show link: set text(fill: if media == "screen" { colors.link } else { colors.text })
  show raw: set text(font: fonts.mono)
  show raw.where(block: true): set text(size: 0.85em)
  show raw.where(block: true): it => block(
    width: 100%, inset: 0.8em, radius: 3pt, fill: colors.code-background, stroke: 0.5pt + colors.border, it,
  )
  show quote.where(block: true): it => block(
    inset: (left: 1em, y: 0.3em), stroke: (left: 2pt + colors.border), {
      it.body
      if it.attribution != none {
        block(above: 0.8em, align(right, text(size: 0.9em, fill: colors.muted, [— #it.attribution])))
      }
    },
  )
  show table.cell: set par(justify: false)
  show table.header: set text(weight: "bold")
  show figure.caption: set text(style: "italic", size: 0.95em)
  show footnote.entry: set text(size: 0.85em)

  if show-title and title != none {
    title-page(title: title, subtitle: subtitle, authors: authors, revision: revision, doctype: doctype)
  }
  if toc != none {
    if facing { pagebreak(weak: true, to: "odd") }
    toc-block(title: toc.title, levels: toc.levels)
    if doctype == "book" { pagebreak(weak: true) }
  }
  // NOTE the converter calls nonfacing() before the document when its first block is [%nonfacing]
  if facing { facing-pagebreak() }
  body
}
