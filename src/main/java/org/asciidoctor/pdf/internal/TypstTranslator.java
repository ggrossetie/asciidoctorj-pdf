package org.asciidoctor.pdf.internal;

import static org.asciidoctor.pdf.internal.Markup.string;
import static org.asciidoctor.pdf.internal.Markup.toPlainText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.asciidoctor.ast.Author;
import org.asciidoctor.ast.Cell;
import org.asciidoctor.ast.Column;
import org.asciidoctor.ast.ContentNode;
import org.asciidoctor.ast.DescriptionList;
import org.asciidoctor.ast.DescriptionListEntry;
import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.ListItem;
import org.asciidoctor.ast.PhraseNode;
import org.asciidoctor.ast.RevisionInfo;
import org.asciidoctor.ast.Row;
import org.asciidoctor.ast.Section;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.ast.Table;
import org.asciidoctor.ast.Title;

/**
 * Converts the nodes of an AsciiDoc document to Typst.
 *
 * <p>The output uses the native Typst elements when there is one (heading, list, table, raw, quote, footnote, link…)
 * so that a template can style them with show rules. The other AsciiDoc elements (admonition, sidebar, example…) are
 * calls to the functions of the base template ({@code asciidoctor.typ}), that a template can redefine.
 */
public final class TypstTranslator {

    /** Path of the base template, relative to the root of the Typst project. */
    public static final String BASE_TEMPLATE = "asciidoctor.typ";

    private static final Pattern CALLOUT = Pattern.compile("#callout\\((\\d+)\\)");
    private static final Pattern LENGTH = Pattern.compile("(\\d+(?:\\.\\d+)?)(%|px|pt|in|cm|mm|vw|em)?");
    /** Attributes that are not useful to a template. */
    private static final Pattern INTERNAL_ATTRIBUTE = Pattern.compile(
            "(asciidoctor|backend|basebackend|doctype|filetype|safe-mode|outfilesuffix|embedded|htmlsyntax|"
                    + "user-home|max-include-depth|attribute-|iconfont|stylesheet|linkcss|copycss|webfonts|"
                    + "prewrap|stylesdir|notitle|showtitle|caution-caption|important-caption|note-caption|"
                    + "tip-caption|warning-caption|[a-z]+-refsig).*");

    private final Markup markup = new Markup();
    private final ImageResolver imageResolver;
    /** The import of the base template, or {@code null} to inline it. */
    private final String baseImport;
    /** The import of the template, or {@code null} to use the base template. */
    private final String templateImport;

    public TypstTranslator(ImageResolver imageResolver, String baseImport, String templateImport) {
        this.imageResolver = imageResolver;
        this.baseImport = baseImport;
        this.templateImport = templateImport;
    }

    public String convert(ContentNode node, String transform) {
        return switch (transform == null ? node.getNodeName() : transform) {
            case "document" -> document((Document) node, true);
            case "embedded" -> document((Document) node, false);
            case "section" -> section((Section) node);
            case "preamble", "open" -> open((StructuralNode) node);
            case "paragraph" -> paragraph((StructuralNode) node);
            case "admonition" -> admonition((StructuralNode) node);
            case "example" -> titledBlock("example", (StructuralNode) node);
            case "sidebar" -> titledBlock("sidebar", (StructuralNode) node);
            case "quote" -> quote((StructuralNode) node);
            case "verse" -> verse((StructuralNode) node);
            case "listing", "literal" -> listing((StructuralNode) node);
            case "pass" -> String.valueOf(((StructuralNode) node).getContent());
            case "stem" -> stem((StructuralNode) node);
            case "image" -> image((StructuralNode) node);
            case "audio", "video" -> media((StructuralNode) node);
            case "ulist" -> ulist((org.asciidoctor.ast.List) node);
            case "olist" -> olist((org.asciidoctor.ast.List) node);
            case "colist" -> colist((org.asciidoctor.ast.List) node);
            case "dlist" -> dlist((DescriptionList) node);
            case "table" -> table((Table) node);
            case "toc" -> toc(node.getDocument(), node);
            case "floating_title" -> floatingTitle((StructuralNode) node);
            case "thematic_break" -> "#thematic-break()";
            case "page_break" -> "#pagebreak()";
            case "inline_quoted" -> inlineQuoted((PhraseNode) node);
            case "inline_anchor" -> inlineAnchor((PhraseNode) node);
            case "inline_break" -> ((PhraseNode) node).getText() + code("#linebreak()");
            case "inline_button" -> code("#btn[") + ((PhraseNode) node).getText() + code("]");
            case "inline_callout" -> code("#callout(" + ((PhraseNode) node).getText() + ")");
            case "inline_footnote" -> inlineFootnote((PhraseNode) node);
            case "inline_image" -> inlineImage((PhraseNode) node);
            case "inline_indexterm" -> "visible".equals(((PhraseNode) node).getType())
                    ? ((PhraseNode) node).getText() : "";
            case "inline_kbd" -> code("#kbd(" + array((List<?>) node.getAttribute("keys")) + ")");
            case "inline_menu" -> inlineMenu((PhraseNode) node);
            default -> unsupported(node, transform);
        };
    }

    // Document

    private String document(Document doc, boolean standalone) {
        StringBuilder out = new StringBuilder();
        if (!standalone) {
            // an embedded document (e.g., an AsciiDoc table cell) is part of the enclosing document
            return blocks(doc);
        }
        out.append("// Generated by AsciidoctorJ PDF\n");
        if (baseImport == null) {
            out.append(BaseTemplate.source()).append('\n');
        } else {
            out.append("#import ").append(string(baseImport)).append(": *\n");
        }
        if (templateImport != null) {
            out.append("#import ").append(string(templateImport)).append(": *\n");
        }
        out.append("\n#show: asciidoctor.with(\n");
        Title title = doc.getStructuredDoctitle();
        if (title != null) {
            arg(out, "title", content(title.getMain()));
            if (title.getSubtitle() != null) {
                arg(out, "subtitle", content(title.getSubtitle()));
            }
        }
        arg(out, "show-title", String.valueOf(!doc.hasAttribute("notitle")));
        List<Author> authors = doc.getAuthors();
        if (!authors.isEmpty()) {
            arg(out, "authors", "(" + authors.stream()
                    .map(a -> "(name: " + string(toPlainText(a.getFullName())) + ", email: " + string(a.getEmail()) + ")")
                    .collect(Collectors.joining(", ")) + ",)");
        }
        RevisionInfo revision = doc.getRevisionInfo();
        if (revision != null && (revision.getNumber() != null || revision.getDate() != null)) {
            arg(out, "revision", "(number: " + string(revision.getNumber()) + ", date: " + string(revision.getDate())
                    + ", remark: " + string(revision.getRemark()) + ")");
        }
        arg(out, "doctype", string(String.valueOf(doc.getAttribute("doctype", "article"))));
        arg(out, "lang", string(String.valueOf(doc.getAttribute("lang", "en"))));
        Object toc = doc.getAttribute("toc");
        if (toc != null && !"macro".equals(doc.getAttribute("toc-placement")) && !"macro".equals(toc)
                && !"preamble".equals(toc)) {
            arg(out, "toc", tocArgs(doc));
        }
        arg(out, "attributes", attributes(doc));
        out.append(")\n\n");
        out.append(blocks(doc));
        return out.toString();
    }

    private String tocArgs(Document doc) {
        return "(title: " + content(String.valueOf(doc.getAttribute("toc-title", "Table of Contents"))) + ", levels: "
                + intAttribute(doc, "toclevels", 2) + ")";
    }

    private static String attributes(Document doc) {
        Map<String, String> attributes = new TreeMap<>();
        doc.getAttributes().forEach((key, value) -> {
            if (value instanceof String s && !INTERNAL_ATTRIBUTE.matcher(key).matches()) {
                attributes.put(key, s);
            }
        });
        if (attributes.isEmpty()) {
            return "(:)";
        }
        return attributes.entrySet().stream()
                .map(e -> "    " + string(e.getKey()) + ": " + string(e.getValue()) + ",\n")
                .collect(Collectors.joining("", "(\n", "  )"));
    }

    private String toc(Document doc, ContentNode node) {
        if (!doc.hasAttribute("toc")) {
            return "";
        }
        String title = node instanceof StructuralNode block && block.getTitle() != null ? block.getTitle()
                : String.valueOf(doc.getAttribute("toc-title", "Table of Contents"));
        return "#toc-block(title: " + content(title) + ", levels: "
                + intAttribute(node, "levels", intAttribute(doc, "toclevels", 2)) + ")";
    }

    // Sections

    private String section(Section node) {
        StringBuilder out = new StringBuilder();
        int level = node.getLevel();
        String title = node.getTitle();
        if (level == 0) {
            out.append("#part(").append(content(title)).append(')');
        } else {
            String numbering = "none";
            Object caption = node.getCaption();
            if (caption != null) {
                // e.g., Appendix A:
                title = caption + title;
            } else if (node.isNumbered() && level <= intAttribute(node.getDocument(), "sectnumlevels", 3)) {
                numbering = "(..) => " + string(node.getSectnum());
            }
            out.append("#heading(level: ").append(level).append(", numbering: ").append(numbering).append(')')
                    .append(content(title));
        }
        label(out, node.getId());
        out.append("\n\n").append(blocks(node));
        return out.toString();
    }

    private String floatingTitle(StructuralNode node) {
        StringBuilder out = new StringBuilder("#heading(level: ").append(Math.max(1, node.getLevel()))
                .append(", outlined: false, bookmarked: false, numbering: none)").append(content(node.getTitle()));
        label(out, node.getId());
        return out.toString();
    }

    // Blocks

    private String paragraph(StructuralNode node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        blockTitle(out, node);
        String markup = toMarkup(String.valueOf(node.getContent()));
        if (node.getRoles().isEmpty()) {
            out.append(markup);
        } else {
            out.append("#role-block(").append(array(node.getRoles())).append(")[").append(markup).append(']');
        }
        return out.toString();
    }

    private String open(StructuralNode node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        String style = node.getStyle();
        if ("abstract".equals(style)) {
            out.append("#abstract(title: ").append(content(node.getTitle())).append(")[\n")
                    .append(blockContent(node)).append("\n]");
        } else {
            blockTitle(out, node);
            out.append(blockContent(node));
        }
        if ("preamble".equals(node.getContext()) && "preamble".equals(node.getDocument().getAttribute("toc"))) {
            out.append("\n\n").append(toc(node.getDocument(), node.getDocument()));
        }
        return out.toString();
    }

    private String admonition(StructuralNode node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        Object name = node.getAttribute("name", String.valueOf(node.getStyle()).toLowerCase(Locale.ROOT));
        Object textlabel = node.getAttribute("textlabel", node.getCaption());
        out.append("#admonition(").append(string(String.valueOf(name)))
                .append(", caption: ").append(textlabel == null ? "none" : content(String.valueOf(textlabel)))
                .append(", title: ").append(content(node.getTitle()))
                .append(")[\n").append(blockContent(node)).append("\n]");
        return out.toString();
    }

    private String titledBlock(String function, StructuralNode node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        out.append('#').append(function).append("(title: ").append(content(captionedTitle(node))).append(")[\n")
                .append(blockContent(node)).append("\n]");
        return out.toString();
    }

    private String quote(StructuralNode node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        blockTitle(out, node);
        out.append("#quote(block: true, attribution: ").append(attribution(node)).append(")[\n")
                .append(blockContent(node)).append("\n]");
        return out.toString();
    }

    private String verse(StructuralNode node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        blockTitle(out, node);
        out.append("#verse(attribution: ").append(attribution(node)).append(")[\n")
                .append(markup.toVerbatimMarkup(String.valueOf(node.getContent()))).append("\n]");
        return out.toString();
    }

    private String attribution(StructuralNode node) {
        Object attribution = node.getAttribute("attribution");
        Object citetitle = node.getAttribute("citetitle");
        if (attribution == null && citetitle == null) {
            return "none";
        }
        List<String> parts = new ArrayList<>();
        if (attribution != null) {
            parts.add(toMarkup(String.valueOf(attribution)));
        }
        if (citetitle != null) {
            parts.add("#emph[" + toMarkup(String.valueOf(citetitle)) + "]");
        }
        return "[" + String.join(", ", parts) + "]";
    }

    private String listing(StructuralNode node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        blockTitle(out, node);
        String source = String.valueOf(node.getContent());
        Object language = "source".equals(node.getStyle()) ? node.getAttribute("language") : null;
        String text = verbatimText(source);
        if (text != null) {
            out.append("#raw(").append(string(text)).append(", block: true");
            if (language != null) {
                out.append(", lang: ").append(string(String.valueOf(language)));
            }
            out.append(')');
        } else {
            // the content has inline formatting (e.g., subs=+quotes): it can't be a raw element
            out.append("#verbatim(").append(string(node.getContext())).append(")[")
                    .append(markup.toVerbatimMarkup(source)).append(']');
        }
        return out.toString();
    }

    /**
     * Returns the plain text of a verbatim content (with the callouts replaced by circled numbers), or {@code null}
     * if it contains other inline nodes.
     */
    private String verbatimText(String content) {
        String text = markup.replaceCodes(content, code -> {
            Matcher matcher = CALLOUT.matcher(code);
            if (!matcher.matches()) {
                return null;
            }
            int number = Integer.parseInt(matcher.group(1));
            return number >= 1 && number <= 20 ? new String(Character.toChars(0x2460 + number - 1)) : "(" + number + ")";
        });
        return text == null ? null : Markup.decodeEntities(text);
    }

    private String stem(StructuralNode node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        blockTitle(out, node);
        out.append("#stem(").append(string(String.valueOf(node.getStyle()))).append(", ")
                .append(string(Markup.decodeEntities(String.valueOf(node.getContent())))).append(", block: true)");
        return out.toString();
    }

    private String image(StructuralNode node) {
        StringBuilder out = new StringBuilder();
        String target = String.valueOf(node.getAttribute("target"));
        String alt = String.valueOf(node.getAttribute("alt", target));
        String path = imageResolver.resolve(node, target);
        out.append("#image-block(");
        if (path == null) {
            out.append("none");
        } else {
            out.append("image(").append(string(path)).append(", alt: ").append(string(toPlainText(alt)));
            String width = imageWidth(node);
            if (width != null) {
                out.append(", width: ").append(width);
            }
            out.append(')');
        }
        out.append(", alt: ").append(string(toPlainText(alt)));
        out.append(", caption: ").append(content(captionedTitle(node)));
        Object align = node.getAttribute("align");
        if (align != null) {
            out.append(", align: ").append(string(String.valueOf(align)));
        }
        Object link = node.getAttribute("link");
        if (link != null) {
            out.append(", link: ").append(string(String.valueOf(link)));
        }
        out.append(')');
        label(out, node.getId());
        return out.toString();
    }

    /** Returns the width of an image as a Typst length, or {@code null} to use its intrinsic width. */
    private static String imageWidth(ContentNode node) {
        for (String name : List.of("pdfwidth", "scaledwidth", "width")) {
            Object value = node.getAttribute(name);
            if (value != null) {
                String length = length(String.valueOf(value), "width".equals(name) ? "px" : "%");
                if (length != null) {
                    return length;
                }
            }
        }
        return null;
    }

    private static String length(String value, String defaultUnit) {
        Matcher matcher = LENGTH.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        double number = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2) == null ? defaultUnit : matcher.group(2);
        return switch (unit) {
            // the CSS pixel: 1px = 0.75pt
            case "px" -> format(number * 0.75) + "pt";
            case "vw" -> format(number) + "%";
            default -> format(number) + unit;
        };
    }

    private static String format(double number) {
        return number == Math.rint(number) ? String.valueOf((long) number) : String.valueOf(number);
    }

    private String media(StructuralNode node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        blockTitle(out, node);
        String target = String.valueOf(node.getAttribute("target"));
        String uri = node.mediaUri(target);
        out.append("#link(").append(string(uri)).append(")[").append(toMarkup(uri)).append(']');
        return out.toString();
    }

    // Lists

    private String ulist(org.asciidoctor.ast.List node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        blockTitle(out, node);
        boolean checklist = node.isOption("checklist") || "checklist".equals(node.getStyle());
        out.append("#list(");
        if (checklist) {
            out.append("marker: [],");
        }
        out.append('\n');
        for (StructuralNode item : node.getItems()) {
            ListItem listItem = (ListItem) item;
            String prefix = "";
            if (checklist) {
                prefix = "#checkbox(" + (listItem.hasAttribute("checkbox") ? listItem.hasAttribute("checked") : "none")
                        + ") ";
            }
            out.append("  [").append(prefix).append(listItem(listItem)).append("],\n");
        }
        return out.append(')').toString();
    }

    private String olist(org.asciidoctor.ast.List node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        blockTitle(out, node);
        out.append("#enum(numbering: ").append(string(numbering(node.getStyle())));
        Object start = node.getAttribute("start");
        if (start != null) {
            out.append(", start: ").append(start);
        }
        if (node.isOption("reversed")) {
            out.append(", reversed: true");
        }
        out.append(",\n");
        for (StructuralNode item : node.getItems()) {
            out.append("  [").append(listItem((ListItem) item)).append("],\n");
        }
        return out.append(')').toString();
    }

    private static String numbering(String style) {
        return switch (style == null ? "arabic" : style) {
            case "loweralpha" -> "a.";
            case "upperalpha" -> "A.";
            case "lowerroman" -> "i.";
            case "upperroman" -> "I.";
            case "lowergreek" -> "α.";
            default -> "1.";
        };
    }

    private String colist(org.asciidoctor.ast.List node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        blockTitle(out, node);
        out.append("#colist((\n");
        for (StructuralNode item : node.getItems()) {
            out.append("  [").append(listItem((ListItem) item)).append("],\n");
        }
        return out.append("))").toString();
    }

    private String dlist(DescriptionList node) {
        StringBuilder out = new StringBuilder();
        anchor(out, node.getId());
        blockTitle(out, node);
        String style = node.getStyle();
        if ("qanda".equals(style)) {
            out.append("#qanda((\n");
        } else if ("horizontal".equals(style)) {
            out.append("#hdlist((\n");
        } else {
            out.append("#terms(\n");
        }
        boolean function = "qanda".equals(style) || "horizontal".equals(style);
        for (DescriptionListEntry entry : node.getItems()) {
            String terms = entry.getTerms().stream().map(term -> toMarkup(term.getText()))
                    .collect(Collectors.joining(code("#linebreak()")));
            String description = entry.getDescription() == null ? "" : listItem(entry.getDescription());
            out.append(function ? "  ([" : "  terms.item([").append(terms).append("], [").append(description)
                    .append("]),\n");
        }
        return out.append(function ? "))" : ")").toString();
    }

    private String listItem(ListItem item) {
        StringBuilder out = new StringBuilder();
        anchor(out, item.getId());
        if (item.hasText()) {
            out.append(toMarkup(item.getText()));
        }
        if (!item.getBlocks().isEmpty()) {
            out.append("\n\n").append(blocks(item)).append('\n');
        }
        return out.toString();
    }

    // Tables

    private String table(Table node) {
        StringBuilder out = new StringBuilder();
        String title = captionedTitle(node);
        out.append("#table-block(caption: ").append(content(title));
        Object width = node.getAttribute("tablepcwidth");
        if (width != null && !"100".equals(String.valueOf(width)) && !node.isOption("autowidth")) {
            out.append(", width: ").append(width).append('%');
        }
        if (node.getAttribute("align") != null) {
            out.append(", align: ").append(string(String.valueOf(node.getAttribute("align"))));
        }
        out.append(", table(\n");
        List<Column> columns = node.getColumns();
        out.append("  columns: (").append(columns.stream()
                .map(col -> node.isOption("autowidth") || col.isOption("autowidth") ? "auto"
                        : col.getAttribute("colpcwidth", col.getWidth()) + "fr")
                .collect(Collectors.joining(", "))).append(columns.size() == 1 ? ",)" : ")").append(",\n");
        out.append("  align: (").append(columns.stream()
                .map(col -> alignment(col.getAttribute("halign"), col.getAttribute("valign")))
                .collect(Collectors.joining(", "))).append(columns.size() == 1 ? ",)" : ")").append(",\n");
        out.append("  stroke: table-stroke(").append(string(node.getFrame())).append(", ")
                .append(string(node.getGrid())).append("),\n");
        Object stripes = node.getAttribute("stripes");
        if (stripes != null) {
            out.append("  fill: table-fill(").append(string(String.valueOf(stripes))).append("),\n");
        }
        if (!node.getHeader().isEmpty()) {
            out.append("  table.header(\n");
            rows(out, node.getHeader(), true);
            out.append("  ),\n");
        }
        rows(out, node.getBody(), false);
        if (!node.getFooter().isEmpty()) {
            out.append("  table.footer(\n");
            rows(out, node.getFooter(), false);
            out.append("  ),\n");
        }
        out.append("))");
        label(out, node.getId());
        return out.toString();
    }

    private void rows(StringBuilder out, List<Row> rows, boolean header) {
        for (Row row : rows) {
            out.append("   ");
            for (Cell cell : row.getCells()) {
                out.append(' ');
                List<String> args = new ArrayList<>();
                if (cell.getColspan() > 1) {
                    args.add("colspan: " + cell.getColspan());
                }
                if (cell.getRowspan() > 1) {
                    args.add("rowspan: " + cell.getRowspan());
                }
                Column column = cell.getColumn();
                Object halign = cell.getAttribute("halign");
                Object valign = cell.getAttribute("valign");
                if (column == null || !alignment(halign, valign).equals(
                        alignment(column.getAttribute("halign"), column.getAttribute("valign")))) {
                    args.add("align: " + alignment(halign, valign));
                }
                if (!args.isEmpty()) {
                    out.append("table.cell(").append(String.join(", ", args)).append(')');
                }
                out.append('[').append(cell(cell, header)).append("],");
            }
            out.append('\n');
        }
    }

    private String cell(Cell cell, boolean header) {
        String style = header ? null : cell.getStyle();
        if ("asciidoc".equals(style)) {
            return "\n" + cell.getContent() + "\n";
        }
        if ("literal".equals(style)) {
            return "#raw(" + string(Markup.decodeEntities(cell.getText())) + ", block: true)";
        }
        List<String> paragraphs = new ArrayList<>();
        for (String paragraph : cell.getText().split("\n[ \t]*\n")) {
            String markup = toMarkup(paragraph);
            paragraphs.add(switch (style == null ? "" : style) {
                case "emphasis" -> "#emph[" + markup + "]";
                case "strong", "header" -> "#strong[" + markup + "]";
                case "monospaced" -> "#mono[" + markup + "]";
                default -> markup;
            });
        }
        return String.join("\n\n", paragraphs);
    }

    private static String alignment(Object halign, Object valign) {
        String horizontal = halign == null ? "left" : String.valueOf(halign);
        String vertical = switch (valign == null ? "top" : String.valueOf(valign)) {
            case "middle" -> "horizon";
            case "bottom" -> "bottom";
            default -> "top";
        };
        return horizontal + " + " + vertical;
    }

    // Inline nodes

    private String inlineQuoted(PhraseNode node) {
        String text = node.getText();
        String type = node.getType() == null ? "unquoted" : node.getType();
        String result = switch (type) {
            case "strong" -> code("#strong[") + text + code("]");
            case "emphasis" -> code("#emph[") + text + code("]");
            case "monospaced" -> code("#mono[") + text + code("]");
            case "superscript" -> code("#super[") + text + code("]");
            case "subscript" -> code("#sub[") + text + code("]");
            case "mark" -> code("#highlight[") + text + code("]");
            case "double" -> "“" + text + "”";
            case "single" -> "‘" + text + "’";
            case "asciimath", "latexmath" -> code("#stem(" + string(type) + ", "
                    + string(toPlainText(text)) + ")");
            default -> text;
        };
        List<String> roles = node.getRoles();
        if (!roles.isEmpty()) {
            result = code("#phrase(" + array(roles) + ")[") + result + code("]");
        }
        if (node.getId() != null) {
            result = code("#metadata(none)#label(" + string(node.getId()) + ")") + result;
        }
        return result;
    }

    private String inlineAnchor(PhraseNode node) {
        String type = node.getType() == null ? "" : node.getType();
        switch (type) {
            case "link" -> {
                return code("#link(" + string(node.getTarget()) + ")[") + node.getText() + code("]");
            }
            case "xref" -> {
                Object refid = node.getAttribute("refid");
                Map<String, Object> refs = node.getDocument().getCatalog().getRefs();
                Object ref = refid == null ? null : refs.get(String.valueOf(refid));
                String text = node.getText();
                if (text == null) {
                    if (ref instanceof ContentNode refNode && refNode.isReftext()) {
                        text = refNode.getReftext();
                    } else if (ref instanceof StructuralNode block && block.getTitle() != null) {
                        text = block.getTitle();
                    } else {
                        text = "[" + refid + "]";
                    }
                }
                if (ref == null || node.getAttribute("path") != null) {
                    return text;
                }
                return code("#link(label(" + string(String.valueOf(refid)) + "))[") + text + code("]");
            }
            case "ref" -> {
                return code("#metadata(none)#label(" + string(node.getId()) + ")");
            }
            case "bibref" -> {
                String reftext = node.getReftext();
                return code("#metadata(none)#label(" + string(node.getId()) + ")") + "["
                        + (reftext != null ? reftext : node.getId()) + "]";
            }
            default -> {
                return node.getText();
            }
        }
    }

    private String inlineFootnote(PhraseNode node) {
        Object index = node.getAttribute("index");
        if (index == null) {
            return "xref".equals(node.getType()) ? "[" + node.getText() + "]" : "";
        }
        String label = string("_footnotedef_" + index);
        if ("xref".equals(node.getType())) {
            return code("#footnote(label(" + label + "))");
        }
        return code("#footnote[") + node.getText() + code("]#label(" + label + ")");
    }

    private String inlineImage(PhraseNode node) {
        String target = node.getTarget();
        if ("icon".equals(node.getType())) {
            return code("#icon(" + string(target) + ")");
        }
        String path = imageResolver.resolve(node, target);
        Object alt = node.getAttribute("alt", target);
        if (path == null) {
            return "[" + alt + "]";
        }
        StringBuilder out = new StringBuilder("#box(image(").append(string(path)).append(", alt: ")
                .append(string(toPlainText(String.valueOf(alt))));
        String width = imageWidth(node);
        if (width != null) {
            out.append(", width: ").append(width);
        } else {
            Object height = node.getAttribute("height");
            String length = height == null ? null : length(String.valueOf(height), "px");
            if (length != null) {
                out.append(", height: ").append(length);
            }
        }
        out.append("))");
        Object link = node.getAttribute("link");
        if (link != null) {
            return code("#link(" + string(String.valueOf(link)) + ", " + out.substring(1) + ")");
        }
        return code(out.toString());
    }

    private String inlineMenu(PhraseNode node) {
        List<Object> items = new ArrayList<>();
        items.add(node.getAttribute("menu"));
        Object submenus = node.getAttribute("submenus");
        if (submenus instanceof List<?> list) {
            items.addAll(list);
        }
        Object menuitem = node.getAttribute("menuitem");
        if (menuitem != null) {
            items.add(menuitem);
        }
        return code("#menu(" + array(items) + ")");
    }

    // Helpers

    /** The content of a block, according to its content model. */
    private String blockContent(StructuralNode node) {
        return switch (node.getContentModel()) {
            case "simple" -> toMarkup(String.valueOf(node.getContent()));
            case "verbatim", "raw" -> "#raw(" + string(Markup.decodeEntities(String.valueOf(node.getContent())))
                    + ", block: true)";
            // NOTE the content of a compound block joins the blocks with a line feed, Typst needs a blank line
            default -> blocks(node);
        };
    }

    private String blocks(StructuralNode node) {
        StringBuilder out = new StringBuilder();
        for (StructuralNode block : node.getBlocks()) {
            String converted = block.convert();
            if (converted != null && !converted.isEmpty()) {
                out.append(converted).append("\n\n");
            }
        }
        // drop the last blank line
        int length = out.length();
        while (length > 0 && out.charAt(length - 1) == '\n') {
            length--;
        }
        out.setLength(length);
        return out.toString();
    }

    private static String captionedTitle(StructuralNode node) {
        String title = node.getTitle();
        if (title == null) {
            return null;
        }
        String caption = node.getCaption();
        return caption == null ? title : caption + title;
    }

    private void blockTitle(StringBuilder out, StructuralNode node) {
        if (node.getTitle() != null) {
            out.append("#block-title").append(content(node.getTitle())).append('\n');
        }
    }

    /** Adds a label to the element that has just been written. */
    private static void label(StringBuilder out, String id) {
        if (id != null) {
            out.append(" #label(").append(string(id)).append(')');
        }
    }

    /** Adds an anchor (a labeled location) before a block that has no Typst element to attach the label to. */
    private static void anchor(StringBuilder out, String id) {
        if (id != null) {
            out.append("#metadata(none)#label(").append(string(id)).append(")\n");
        }
    }

    private static String array(List<?> values) {
        if (values == null || values.isEmpty()) {
            return "()";
        }
        return values.stream().map(value -> string(toPlainText(String.valueOf(value))))
                .collect(Collectors.joining(", ", "(", values.size() == 1 ? ",)" : ")"));
    }

    private static void arg(StringBuilder out, String name, String value) {
        out.append("  ").append(name).append(": ").append(value).append(",\n");
    }

    private static int intAttribute(ContentNode node, String name, int defaultValue) {
        Object value = node.getAttribute(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String code(String typst) {
        return markup.code(typst);
    }

    private String content(String inline) {
        return markup.content(inline);
    }

    private String toMarkup(String inline) {
        return markup.toMarkup(inline);
    }

    private String unsupported(ContentNode node, String transform) {
        if (node instanceof StructuralNode block) {
            return blocks(block);
        }
        return node instanceof PhraseNode phrase ? phrase.getText() : "";
    }
}
