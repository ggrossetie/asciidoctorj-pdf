package org.asciidoctor.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The print and prepress modes (the media attribute). */
class MediaTest {

    private static Asciidoctor asciidoctor;

    @BeforeAll
    static void createAsciidoctor() {
        asciidoctor = Asciidoctor.Factory.create();
    }

    @AfterAll
    static void closeAsciidoctor() {
        asciidoctor.close();
    }

    // Print: the URI of the links

    @ParameterizedTest
    @ValueSource(strings = {"print", "prepress"})
    void showsTheUriOfALinkInPrint(String media) throws IOException {
        assertThat(lines("https://asciidoctor.org[Asciidoctor] is a text processor.", Map.of("media", media)))
                .containsExactly("Asciidoctor [https://asciidoctor.org] is a text processor.");
    }

    @Test
    void doesNotShowTheUriOfALinkOnScreen() throws IOException {
        assertThat(lines("https://asciidoctor.org[Asciidoctor] is a text processor.", Map.of()))
                .containsExactly("Asciidoctor is a text processor.");
    }

    @Test
    void showsTheUriOfALinkWhenShowLinkUriIsSet() throws IOException {
        assertThat(lines("""
                :show-link-uri:

                https://asciidoctor.org[Asciidoctor] is a text processor.
                """, Map.of()))
                .containsExactly("Asciidoctor [https://asciidoctor.org] is a text processor.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"print", "prepress"})
    void doesNotShowTheUriOfALinkWhenShowLinkUriIsUnsetInTheDocument(String media) throws IOException {
        assertThat(lines("""
                :!show-link-uri:

                https://asciidoctor.org[Asciidoctor] is a text processor.
                """, Map.of("media", media)))
                .containsExactly("Asciidoctor is a text processor.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"show-link-uri!", "!show-link-uri@"})
    void doesNotShowTheUriOfALinkWhenShowLinkUriIsUnsetFromTheApi(String unset) throws IOException {
        assertThat(lines("https://asciidoctor.org[Asciidoctor] is a text processor.",
                Map.of("media", "print", unset, "")))
                .containsExactly("Asciidoctor is a text processor.");
    }

    @Test
    void doesNotShowTheUriOfALinkWhenShowLinkUriIsUnsetInAnAsciiDocTableCell() throws IOException {
        assertThat(lines("""
                |===
                a|
                :!show-link-uri:

                https://asciidoctor.org[Asciidoctor]
                |===
                """, Map.of("media", "print")))
                .containsExactly("Asciidoctor");
    }

    @Test
    void doesNotShowTheUriOfABareLinkTwice() throws IOException {
        assertThat(lines("Go to https://asciidoctor.org.", Map.of("media", "print")))
                .containsExactly("Go to https://asciidoctor.org.");
    }

    @Test
    void showsTheUriOfALinkWithoutItsSchemeWhenHideUriSchemeIsSet() throws IOException {
        assertThat(lines("Just ask https://chatgpt.com[ChatGPT].", Map.of("media", "print", "hide-uri-scheme", "")))
                .containsExactly("Just ask ChatGPT [chatgpt.com].");
    }

    @Test
    void showsTheAddressOfAnEmailLinkWithText() throws IOException {
        assertThat(lines("mailto:doc@example.org[Write to us] or doc@example.org.", Map.of("media", "print")))
                .containsExactly("Write to us [mailto:doc@example.org] or doc@example.org.");
        assertThat(lines("mailto:doc@example.org[Write to us].", Map.of("media", "print", "hide-uri-scheme", "")))
                .containsExactly("Write to us [doc@example.org].");
    }

    @Test
    void breaksTheLineInTheUriOfALink() throws IOException {
        List<String> lines = lines("""
                The URL on this line will get split on the / character
                https://github.com/asciidoctor/asciidoctor/issues?q=milestone%3Av2.0.x[link]
                """, Map.of("media", "print"));
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).contains("link [https://github.com/").matches(".*[/?]");
        assertThat(lines.get(0) + lines.get(1)).endsWith("link [https://github.com/asciidoctor/asciidoctor/issues?q=milestone%3Av2.0.x]");
    }

    // Prepress: the facing pages

    @Test
    void startsTheBodyOfABookOnARectoPage() throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :doctype: book

                Preface content.

                == Chapter Title

                Chapter content.
                """, Map.of("media", "prepress"));
        assertThat(pdf.pages()).hasSize(5);
        assertThat(pdf.page("Document Title")).isEqualTo(1);
        assertThat(pdf.page("Preface content.")).isEqualTo(3);
        assertThat(pdf.page("Chapter Title")).isEqualTo(5);
    }

    @Test
    void startsTheTableOfContentsAndTheChaptersOfABookOnARectoPage() throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :doctype: book
                :toc:

                == Beginning

                == Middle

                == End
                """, Map.of("media", "prepress"));
        assertThat(pdf.pages()).hasSize(9);
        assertThat(pdf.page("Document Title")).isEqualTo(1);
        assertThat(pdf.page("Table of Contents")).isEqualTo(3);
        assertThat(pdf.pages("Beginning")).containsExactly(3, 5);
        assertThat(pdf.pages("Middle")).containsExactly(3, 7);
        assertThat(pdf.pages("End")).containsExactly(3, 9);
    }

    @Test
    void startsTheTableOfContentsOnTheFirstPageWithoutTitlePage() throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :doctype: book
                :notitle:
                :toc:

                == Chapter Title

                Chapter content.
                """, Map.of("media", "prepress"));
        assertThat(pdf.pages()).hasSize(3);
        assertThat(pdf.page("Table of Contents")).isEqualTo(1);
        assertThat(pdf.pages("Chapter Title")).containsExactly(1, 3);
    }

    @Test
    void startsAPartAndItsFirstChapterOnARectoPage() throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :doctype: book

                = Part One

                == Chapter One

                = Part Two

                == Chapter Two
                """, Map.of("media", "prepress"));
        assertThat(pdf.page("Part One")).isEqualTo(3);
        assertThat(pdf.page("Chapter One")).isEqualTo(5);
        assertThat(pdf.page("Part Two")).isEqualTo(7);
        assertThat(pdf.page("Chapter Two")).isEqualTo(9);
    }

    @Test
    void startsANonfacingChapterAfterThePreambleOnTheNextPage() throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :doctype: book

                preamble

                [%nonfacing]
                == Chapter
                """, Map.of("media", "prepress"));
        assertThat(pdf.page("Chapter")).isEqualTo(4);
    }

    @Test
    void startsANonfacingFirstChapterOnTheNextPage() throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :doctype: book

                [%nonfacing]
                == Chapter

                == Next Chapter
                """, Map.of("media", "prepress"));
        assertThat(pdf.page("Chapter")).isEqualTo(2);
        assertThat(pdf.page("Next Chapter")).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"screen", "print"})
    void doesNotInsertBlankPagesOutsidePrepress(String media) throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :doctype: book

                == Beginning

                == End
                """, Map.of("media", media));
        assertThat(pdf.pages()).hasSize(3);
        assertThat(pdf.page("End")).isEqualTo(3);
    }

    @Test
    void doesNotInsertBlankPagesInAnArticle() throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :toc:

                == Beginning

                <<<

                == End
                """, Map.of("media", "prepress"));
        assertThat(pdf.pages()).hasSize(2);
    }

    // Prepress: the mirrored margins

    @Test
    void mirrorsTheMarginsInPrepress() throws IOException {
        String source = """
                = Document Title

                Recto page.

                <<<

                Verso page.
                """;
        Pdf prepress = convert(source, Map.of("media", "prepress"));
        // 2.25cm (inner) on the left of a recto page, 1.75cm (outer) on the left of a verso page
        assertThat(prepress.x("Recto page.")).isCloseTo(cm(2.25), Offset.offset(1.0f));
        assertThat(prepress.x("Verso page.")).isCloseTo(cm(1.75), Offset.offset(1.0f));

        Pdf screen = convert(source, Map.of());
        assertThat(screen.x("Recto page.")).isCloseTo(cm(2), Offset.offset(1.0f));
        assertThat(screen.x("Verso page.")).isCloseTo(cm(2), Offset.offset(1.0f));
    }

    @Test
    void appliesTheMarginsOfATemplate(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("theme.typ"), """
                #import "/asciidoctor.typ" as base: *
                #let asciidoctor = base.asciidoctor.with(margin: 1cm, margin-inner: 3cm, margin-outer: none)
                """);
        Pdf pdf = convert("""
                Recto page.

                <<<

                Verso page.
                """, Map.of("media", "prepress", "pdf-template", tempDir.resolve("theme.typ").toString()));
        assertThat(pdf.x("Recto page.")).isCloseTo(cm(3), Offset.offset(1.0f));
        assertThat(pdf.x("Verso page.")).isCloseTo(cm(1), Offset.offset(1.0f));
    }

    private static float cm(double value) {
        return (float) (value / 2.54 * 72);
    }

    // Helpers

    private static List<String> lines(String source, Map<String, String> attributes) throws IOException {
        return convert(source, attributes).pages().get(0).lines().stream()
                .map(line -> line.replace("​", ""))
                // the page number
                .filter(line -> !line.matches("\\d+"))
                .toList();
    }

    private static Pdf convert(String source, Map<String, String> attributes) throws IOException {
        var builder = Attributes.builder().attribute("reproducible");
        attributes.forEach(builder::attribute);
        byte[] bytes = asciidoctor.convert(source, Options.builder()
                .backend("pdf")
                .standalone(true)
                .safe(SafeMode.UNSAFE)
                .attributes(builder.build())
                .build(), byte[].class);
        try (PDDocument document = Loader.loadPDF(bytes)) {
            List<Page> pages = new ArrayList<>();
            for (int number = 1; number <= document.getNumberOfPages(); number++) {
                PositionStripper stripper = new PositionStripper();
                stripper.setStartPage(number);
                stripper.setEndPage(number);
                String text = stripper.getText(document);
                pages.add(new Page(text.lines().filter(line -> !line.isBlank()).toList(), stripper.lineStarts));
            }
            return new Pdf(pages);
        }
    }

    private record Page(List<String> lines, Map<String, Float> lineStarts) {
    }

    private record Pdf(List<Page> pages) {

        /** The numbers of the pages that contain a line that starts with the text. */
        List<Integer> pages(String text) {
            List<Integer> numbers = new ArrayList<>();
            for (int i = 0; i < pages.size(); i++) {
                if (pages.get(i).lines().stream().anyMatch(line -> line.startsWith(text))) {
                    numbers.add(i + 1);
                }
            }
            return numbers;
        }

        /** The number of the first page that contains a line that starts with the text. */
        int page(String text) {
            List<Integer> numbers = pages(text);
            return numbers.isEmpty() ? -1 : numbers.get(0);
        }

        /** The horizontal position of the line that starts with the text. */
        float x(String text) {
            return pages.stream().map(page -> page.lineStarts().get(text)).filter(x -> x != null).findFirst()
                    .orElseThrow();
        }
    }

    /** Records the horizontal position of the start of each line. */
    private static final class PositionStripper extends PDFTextStripper {

        private final Map<String, Float> lineStarts = new HashMap<>();

        PositionStripper() throws IOException {
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (!positions.isEmpty()) {
                lineStarts.putIfAbsent(text.strip(), positions.get(0).getXDirAdj());
            }
            super.writeString(text, positions);
        }
    }
}
