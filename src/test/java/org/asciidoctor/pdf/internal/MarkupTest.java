package org.asciidoctor.pdf.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkupTest {

    private final Markup markup = new Markup();

    @Test
    void escapesTheMarkupCharacters() {
        assertThat(markup.toMarkup("5 * 3 = 15, a_b, $1, #x, [y], <z>, @m, ~t, 'q' \"d\" `c` \\"))
                .isEqualTo("5 \\* 3 \\= 15, a\\_b, \\$1, \\#x, \\[y\\], \\<z>, \\@m, \\~t, \\'q\\' \\\"d\\\" \\`c\\` \\\\");
    }

    @Test
    void escapesTheShorthandsOnly() {
        assertThat(markup.toMarkup("well-known -- dash -? a... // b /* c */ http://x"))
                .isEqualTo("well-known \\-- dash \\-? a\\.\\.. \\// b \\/\\* c \\*/ http:\\//x");
    }

    @Test
    void decodesTheEntities() {
        assertThat(markup.toMarkup("&lt;a&gt; &amp; &#8217; &#x2014;")).isEqualTo("\\<a> & \u2019 \u2014");
    }

    @Test
    void keepsTheCodeOfTheInlineNodes() {
        assertThat(markup.toMarkup("a " + markup.code("#strong[") + "*b*" + markup.code("]") + "(c)"))
                .isEqualTo("a #strong[\\*b\\*]\\(c)");
    }

    @Test
    void escapesAListMarkerAtTheStart() {
        assertThat(markup.toMarkup("- a")).isEqualTo("\\- a");
        assertThat(markup.toMarkup("12. a")).isEqualTo("12\\. a");
        assertThat(markup.toMarkup("/ a")).isEqualTo("\\/ a");
    }

    @Test
    void replacesTheLineFeedsWithSpaces() {
        assertThat(markup.toMarkup("a\nb")).isEqualTo("a b");
    }

    @Test
    void preservesTheSpacesAndLineFeedsOfAVerbatimContent() {
        assertThat(markup.toVerbatimMarkup("a\n  b c")).isEqualTo("a\\\n~~b c");
        assertThat(markup.toVerbatimMarkup("a " + markup.code("#f(\"x  y\")") + "  b")).isEqualTo("a #f(\"x  y\")~~b");
    }

    @Test
    void ignoresTheCharactersOfTheCodeInTheSubstitutions() {
        String strong = markup.code("#strong[") + "a" + markup.code("]");
        assertThat(strong).doesNotContain("#", "[", "]");
    }

    @Test
    void returnsTheTextOfAnInlineContent() {
        assertThat(Markup.toPlainText("a " + markup.code("#strong[") + "b" + markup.code("]") + " &amp;")).isEqualTo("a b &");
    }
}
