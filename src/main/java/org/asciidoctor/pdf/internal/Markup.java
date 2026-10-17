package org.asciidoctor.pdf.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the text produced by the AsciiDoc substitutions into Typst markup.
 *
 * <p>The plain text segments of an inline content are not seen by the converter: the substitutions splice the output
 * of the inline nodes (strong, link…) into the text. So the Typst code of an inline node is replaced by a token
 * ({@link #code(String)}), and {@link #toMarkup(String)} escapes the text and puts the code back once the whole inline
 * content is known (paragraph, title, list item…).
 *
 * <p>A token is an index enclosed in Unicode noncharacters, reserved for internal use: it doesn't clash with the text
 * of a document, and the substitutions that run after the inline node is converted can't match the characters of the
 * Typst code (for instance, the mark substitution would match the # of a function call).
 */
public final class Markup {

    static final char OPEN = '﷐';
    static final char CLOSE = '﷑';

    private static final Pattern ENTITY = Pattern.compile("&(?:#(\\d+)|#x([0-9a-fA-F]+)|(lt|gt|amp|quot|apos));");
    private static final Map<String, String> NAMED_ENTITIES = Map.of(
            "lt", "<", "gt", ">", "amp", "&", "quot", "\"", "apos", "'");

    private final List<String> codes = new ArrayList<>();

    /** Returns a token that stands for a fragment of Typst code in an inline content. */
    public String code(String typst) {
        codes.add(typst);
        return OPEN + Integer.toString(codes.size() - 1) + CLOSE;
    }

    /**
     * Converts an inline content (the text and the tokens of the inline nodes) to Typst markup: the text is escaped,
     * the HTML entities of the substitutions are decoded and the line feeds become spaces.
     */
    public String toMarkup(String content) {
        return convert(content, false);
    }

    /**
     * Converts the content of a verbatim block that contains inline nodes (for instance, when the quotes substitution
     * is enabled on a listing) to Typst markup that preserves the spaces and the line feeds.
     */
    public String toVerbatimMarkup(String content) {
        return convert(content, true);
    }

    /** Returns a Typst content block containing the markup of an inline content, or {@code none}. */
    public String content(String inline) {
        return inline == null ? "none" : "[" + toMarkup(inline) + "]";
    }

    /** Returns the plain text of an inline content (without the inline nodes), for instance to use it in a string. */
    public static String toPlainText(String content) {
        if (content == null) {
            return null;
        }
        return decodeEntities(content.replaceAll(OPEN + "\\d+" + CLOSE, ""));
    }

    /**
     * Replaces the tokens of an inline content with the result of a function of their Typst code, or returns
     * {@code null} if the function returns {@code null} for one of them.
     */
    public String replaceCodes(String content, java.util.function.Function<String, String> replacement) {
        Matcher matcher = Pattern.compile(OPEN + "(\\d+)" + CLOSE).matcher(content);
        StringBuilder out = new StringBuilder(content.length());
        while (matcher.find()) {
            String replaced = replacement.apply(codes.get(Integer.parseInt(matcher.group(1))));
            if (replaced == null) {
                return null;
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replaced));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String convert(String content, boolean verbatim) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(content.length() + 16);
        int start = 0;
        boolean afterCode = false;
        int open = content.indexOf(OPEN);
        while (open >= 0) {
            int close = content.indexOf(CLOSE, open);
            if (close < 0) {
                break;
            }
            String index = content.substring(open + 1, close);
            if (!index.chars().allMatch(Character::isDigit) || index.isEmpty()) {
                open = content.indexOf(OPEN, open + 1);
                continue;
            }
            escapeText(content.substring(start, open), afterCode, verbatim, out);
            out.append(codes.get(Integer.parseInt(index)));
            start = close + 1;
            afterCode = true;
            open = content.indexOf(OPEN, start);
        }
        escapeText(content.substring(start), afterCode, verbatim, out);
        return out.toString();
    }

    public static String decodeEntities(String text) {
        if (text.indexOf('&') < 0) {
            return text;
        }
        Matcher matcher = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            String replacement;
            if (matcher.group(1) != null) {
                replacement = new String(Character.toChars(Integer.parseInt(matcher.group(1))));
            } else if (matcher.group(2) != null) {
                replacement = new String(Character.toChars(Integer.parseInt(matcher.group(2), 16)));
            } else {
                replacement = NAMED_ENTITIES.get(matcher.group(3));
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Returns a Typst string literal. */
    public static String string(String value) {
        if (value == null) {
            return "none";
        }
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static void escapeText(String text, boolean afterCode, boolean verbatim, StringBuilder out) {
        text = decodeEntities(text);
        int length = text.length();
        boolean lineStart = out.isEmpty() || out.charAt(out.length() - 1) == '\n';
        int enumDot = lineStart ? enumMarkerDot(text) : -1;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            char previous = i > 0 ? text.charAt(i - 1) : 0;
            char next = i + 1 < length ? text.charAt(i + 1) : 0;
            if (c == '\r') {
                continue;
            } else if (c == '\n') {
                if (verbatim) {
                    out.append("\\\n");
                    lineStart = true;
                    enumDot = enumMarkerDot(text.substring(i + 1));
                    enumDot = enumDot < 0 ? -1 : enumDot + i + 1;
                    continue;
                }
                out.append(' ');
            } else if (verbatim && c == ' ' && (lineStart || previous == ' ' || next == ' ')) {
                // Typst collapses the spaces, unless they are non-breaking
                out.append('~');
                continue;
            } else if ((i == 0 && afterCode || lineStart) && isAsciiPunctuation(c) || i == enumDot) {
                // a parenthesis, a bracket or a dot right after a Typst expression would continue it, and a list
                // marker at the start of a line would start a list
                out.append('\\').append(c);
            } else if (needsEscape(c, next)) {
                out.append('\\').append(c);
            } else {
                out.append(c);
            }
            lineStart = false;
        }
    }

    /** Returns the index of the dot of an enumeration marker (e.g., 1.) that starts the text, or -1. */
    private static int enumMarkerDot(String text) {
        int i = 0;
        while (i < text.length() && Character.isDigit(text.charAt(i))) {
            i++;
        }
        return i > 0 && i < text.length() && text.charAt(i) == '.' ? i : -1;
    }

    private static boolean needsEscape(char c, char next) {
        return switch (c) {
            case '\\', '*', '_', '`', '$', '#', '[', ']', '<', '@', '~', '\'', '"', '=', '+' -> true;
            // shorthands (en dash, em dash, soft hyphen, ellipsis), comments and URLs (://)
            case '-' -> next == '-' || next == '?';
            case '.' -> next == '.';
            case '/' -> next == '/' || next == '*';
            default -> false;
        };
    }

    private static boolean isAsciiPunctuation(char c) {
        return c >= 33 && c < 128 && !Character.isLetterOrDigit(c);
    }
}
