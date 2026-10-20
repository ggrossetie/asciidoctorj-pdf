package org.asciidoctor.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The front cover (front-cover-image). */
class CoverTest {

    /** The size of an A4 page, in points. */
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final Offset<Float> TOLERANCE = Offset.offset(1.0f);

    private static Asciidoctor asciidoctor;

    @TempDir
    Path dir;

    @BeforeAll
    static void createAsciidoctor() {
        asciidoctor = Asciidoctor.Factory.create();
    }

    @AfterAll
    static void closeAsciidoctor() {
        asciidoctor.close();
    }

    @BeforeEach
    void createImages() throws IOException {
        Files.createDirectories(dir.resolve("images"));
        // 300 x 150 px, at 72 dpi: 300 x 150 pt
        Files.copy(Path.of("src/test/resources/images/cover.png"), dir.resolve("images/cover.png"));
    }

    @Test
    void addsTheFrontCoverBeforeTheTitlePage() throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :doctype: book
                :front-cover-image: image:cover.png[]

                == Chapter
                """, Map.of());
        assertThat(pdf.texts()).hasSize(3);
        assertThat(pdf.texts().get(0)).isBlank();
        assertThat(pdf.texts().get(1)).contains("Document Title");
        assertThat(pdf.images().get(0)).hasSize(1);
        assertThat(pdf.images().get(1)).isEmpty();
    }

    @Test
    void scalesTheCoverToFitThePageByDefault() throws IOException {
        Bounds cover = cover("image:cover.png[]");
        assertThat(cover.width()).isCloseTo(PAGE_WIDTH, TOLERANCE);
        assertThat(cover.height()).isCloseTo(PAGE_WIDTH / 2, TOLERANCE);
        assertThat(cover.x()).isCloseTo(0, TOLERANCE);
        assertThat(cover.y()).isCloseTo((PAGE_HEIGHT - PAGE_WIDTH / 2) / 2, TOLERANCE);
    }

    @Test
    void acceptsAPath() throws IOException {
        Bounds cover = cover("cover.png");
        assertThat(cover.width()).isCloseTo(PAGE_WIDTH, TOLERANCE);
    }

    @Test
    void scalesTheCoverToCoverThePage() throws IOException {
        Bounds cover = cover("image:cover.png[fit=cover]");
        assertThat(cover.height()).isCloseTo(PAGE_HEIGHT, TOLERANCE);
        assertThat(cover.width()).isCloseTo(PAGE_HEIGHT * 2, TOLERANCE);
        assertThat(cover.x()).isCloseTo((PAGE_WIDTH - PAGE_HEIGHT * 2) / 2, TOLERANCE);
    }

    @Test
    void stretchesTheCoverToFillThePage() throws IOException {
        Bounds cover = cover("image:cover.png[fit=fill]");
        assertThat(cover.width()).isCloseTo(PAGE_WIDTH, TOLERANCE);
        assertThat(cover.height()).isCloseTo(PAGE_HEIGHT, TOLERANCE);
    }

    @Test
    void keepsTheNaturalSizeOfTheCoverAtTheGivenPosition() throws IOException {
        Bounds cover = cover("image:cover.png[fit=none,position=top left]");
        assertThat(cover.width()).isCloseTo(300, TOLERANCE);
        assertThat(cover.height()).isCloseTo(150, TOLERANCE);
        assertThat(cover.x()).isCloseTo(0, TOLERANCE);
        assertThat(cover.y()).isCloseTo(PAGE_HEIGHT - 150, TOLERANCE);

        cover = cover("image:cover.png[fit=scale-down,position=bottom]");
        assertThat(cover.width()).isCloseTo(300, TOLERANCE);
        assertThat(cover.x()).isCloseTo((PAGE_WIDTH - 300) / 2, TOLERANCE);
        assertThat(cover.y()).isCloseTo(0, TOLERANCE);
    }

    @Test
    void usesAPageOfAPdfFileAsCover() throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String text : List.of("First page", "Second page")) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 24);
                    content.newLineAtOffset(100, 400);
                    content.showText(text);
                    content.endText();
                }
            }
            document.save(dir.resolve("images/cover.pdf").toFile());
        }
        Pdf pdf = convert("""
                = Document Title
                :front-cover-image: image:cover.pdf[page=2]

                Content.
                """, Map.of());
        assertThat(pdf.texts()).hasSize(2);
        assertThat(pdf.texts().get(0)).contains("Second page").doesNotContain("First page");
    }

    @Test
    void doesNotNumberTheCover() throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :front-cover-image: image:cover.png[]

                Content.
                """, Map.of());
        assertThat(pdf.texts()).hasSize(2);
        assertThat(pdf.texts().get(0)).isBlank();
        assertThat(pdf.texts().get(1).lines()).contains("1");
    }

    @Test
    void addsABlankPageAfterTheCoverInPrepress() throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :doctype: book
                :front-cover-image: image:cover.png[]

                == Chapter
                """, Map.of("media", "prepress"));
        assertThat(pdf.texts()).hasSize(5);
        assertThat(pdf.texts().get(1)).isBlank();
        assertThat(pdf.images().get(1)).isEmpty();
        assertThat(pdf.texts().get(2)).contains("Document Title");
        assertThat(pdf.texts().get(4)).contains("Chapter");
    }

    @Test
    void ignoresAMissingCover() throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :front-cover-image: image:missing.png[]

                Content.
                """, Map.of());
        assertThat(pdf.texts()).hasSize(1);
        assertThat(pdf.texts().get(0)).contains("Document Title");
    }

    @Test
    void letsATemplateChangeTheCover() throws IOException {
        Files.writeString(dir.resolve("theme.typ"), """
                #import "/asciidoctor.typ" as base: *
                #let cover-image(image, ..args) = {
                  base.cover-image(image, ..args)
                  place(center + horizon, text(size: 30pt)[Template cover])
                }
                """);
        Pdf pdf = convert("""
                = Document Title
                :front-cover-image: image:cover.png[]

                Content.
                """, Map.of("pdf-template", "theme.typ"));
        assertThat(pdf.texts().get(0)).contains("Template cover");
        assertThat(pdf.images().get(0)).hasSize(1);
    }

    // Back cover

    @Test
    void addsTheBackCoverAfterTheLastPage() throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :front-cover-image: image:cover.png[]
                :back-cover-image: image:cover.png[fit=fill]

                Content.
                """, Map.of());
        assertThat(pdf.texts()).hasSize(3);
        assertThat(pdf.texts().get(1)).contains("Content.");
        assertThat(pdf.texts().get(2)).isBlank();
        Bounds cover = pdf.images().get(2).get(0);
        assertThat(cover.width()).isCloseTo(PAGE_WIDTH, TOLERANCE);
        assertThat(cover.height()).isCloseTo(PAGE_HEIGHT, TOLERANCE);
    }

    @Test
    void doesNotCountTheCoversInTheTotalNumberOfPages() throws IOException {
        Files.writeString(dir.resolve("theme.typ"), """
                #import "/asciidoctor.typ" as base: *
                #let asciidoctor(..args, body) = {
                  set page(footer: context align(center, counter(page).display("1 / 1", both: true)))
                  base.asciidoctor(..args, body)
                }
                """);
        Pdf pdf = convert("""
                = Document Title
                :front-cover-image: image:cover.png[]
                :back-cover-image: image:cover.png[]

                First page.

                <<<

                Last page.
                """, Map.of("pdf-template", "theme.typ"));
        assertThat(pdf.texts()).hasSize(4);
        assertThat(pdf.texts().get(1).lines()).contains("1 / 2");
        assertThat(pdf.texts().get(2).lines()).contains("2 / 2");
        assertThat(pdf.texts().get(3)).isBlank();
    }

    @Test
    void putsTheBackCoverOnAVersoPageInPrepress() throws IOException {
        String source = """
                = Document Title
                :doctype: book
                :back-cover-image: image:cover.png[]

                == Chapter

                <<<

                Last page.
                """;
        // title page, blank page, chapter, last page, blank page, back cover
        Pdf pdf = convert(source, Map.of("media", "prepress"));
        assertThat(pdf.texts()).hasSize(6);
        assertThat(pdf.texts().get(3)).contains("Last page.");
        assertThat(pdf.texts().get(4).strip()).matches("\\d*");
        assertThat(pdf.images().get(4)).isEmpty();
        assertThat(pdf.images().get(5)).hasSize(1);

        pdf = convert(source, Map.of());
        assertThat(pdf.texts()).hasSize(4);
        assertThat(pdf.images().get(3)).hasSize(1);
    }

    // Helpers

    private Bounds cover(String value) throws IOException {
        Pdf pdf = convert("""
                = Document Title
                :front-cover-image: %s

                Content.
                """.formatted(value), Map.of());
        assertThat(pdf.images().get(0)).hasSize(1);
        return pdf.images().get(0).get(0);
    }

    private Pdf convert(String source, Map<String, String> attributes) throws IOException {
        var builder = Attributes.builder().attribute("reproducible").attribute("imagesdir", "images");
        attributes.forEach(builder::attribute);
        byte[] bytes = asciidoctor.convert(source, Options.builder()
                .backend("pdf")
                .standalone(true)
                .safe(SafeMode.UNSAFE)
                .baseDir(dir.toFile())
                .attributes(builder.build())
                .build(), byte[].class);
        try (PDDocument document = Loader.loadPDF(bytes)) {
            List<String> texts = new ArrayList<>();
            List<List<Bounds>> images = new ArrayList<>();
            for (int number = 1; number <= document.getNumberOfPages(); number++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(number);
                stripper.setEndPage(number);
                texts.add(stripper.getText(document));
                ImageLocator locator = new ImageLocator(document.getPage(number - 1));
                locator.processPage(document.getPage(number - 1));
                images.add(locator.images);
            }
            return new Pdf(texts, images);
        }
    }

    /** The text and the images of each page. */
    private record Pdf(List<String> texts, List<List<Bounds>> images) {
    }

    /** The bounds of an image, from the bottom left corner of the page, in points. */
    private record Bounds(float x, float y, float width, float height) {
    }

    /** Records the bounds of the images drawn on a page. */
    private static final class ImageLocator extends PDFGraphicsStreamEngine {

        private final List<Bounds> images = new ArrayList<>();

        ImageLocator(PDPage page) {
            super(page);
        }

        @Override
        public void drawImage(PDImage pdImage) {
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            images.add(new Bounds(ctm.getTranslateX(), ctm.getTranslateY(), ctm.getScalingFactorX(),
                    ctm.getScalingFactorY()));
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void moveTo(float x, float y) {
        }

        @Override
        public void lineTo(float x, float y) {
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        }

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Float();
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
        }

        @Override
        public void strokePath() {
        }

        @Override
        public void fillPath(int windingRule) {
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
        }

        @Override
        public void shadingFill(COSName shadingName) {
        }
    }
}
