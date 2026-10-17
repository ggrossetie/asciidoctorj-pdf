package org.asciidoctor.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfConverterTest {

    private static final Path SAMPLE = Path.of("src/test/resources/sample/sample.adoc");

    private static Asciidoctor asciidoctor;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void createAsciidoctor() {
        asciidoctor = Asciidoctor.Factory.create();
    }

    @AfterAll
    static void closeAsciidoctor() {
        asciidoctor.close();
    }

    @Test
    void convertsTheSampleToPdf() throws IOException {
        File output = tempDir.resolve("sample.pdf").toFile();
        asciidoctor.convertFile(SAMPLE.toFile(), Options.builder()
                .backend("pdf")
                .safe(SafeMode.UNSAFE)
                .toFile(output)
                .attributes(Attributes.builder().attribute("reproducible").build())
                .build());

        String text = text(output.toPath());
        assertThat(text)
                .contains("Document Title")
                .contains("A Subtitle")
                .contains("Jane Doe")
                .contains("1. Inline formatting")
                .contains("Text with strong, emphasis, monospace, superscript, subscript, highlight and red text.")
                .contains("5 * 3 = 15,")
                .contains("#hashtag, [brackets], <angle>, @mention")
                .contains("Keys Ctrl + Shift + T , button [Save], menu File\u00a0›\u00a0Save As")
                .contains("A note admonition.")
                .contains("System.out.println(\"Hello <World> & \\\"friends\\\"\");")
                .contains("Spanning two columns")
                .contains("An AsciiDoc cell")
                .contains("Appendix A: An appendix");
    }

    @Test
    void convertsTheSampleToTypst() throws IOException {
        File output = tempDir.resolve("sample.typ").toFile();
        asciidoctor.convertFile(SAMPLE.toFile(), Options.builder()
                .backend("typst")
                .safe(SafeMode.UNSAFE)
                .toFile(output)
                .build());

        String typst = Files.readString(output.toPath());
        assertThat(typst)
                .contains("#let asciidoctor(")
                .contains("#show: asciidoctor.with(")
                .contains("#heading(level: 1, numbering: (..) => \"1.\")[Inline formatting] #label(\"_inline_formatting\")")
                .contains("#admonition(\"note\", caption: [Note], title: none)[");
    }

    @Test
    void appliesATemplate() throws IOException {
        Path templateDir = Files.createDirectories(tempDir.resolve("theme"));
        Files.writeString(templateDir.resolve("theme.typ"), """
                #import "/asciidoctor.typ": *
                #import "footer.typ": footer-text
                #let admonition(name, caption: none, title: none, body) = block[ADMONITION #upper(name): #body]
                #let asciidoctor(title: none, attributes: (:), ..args, body) = {
                  set page(footer: [#footer-text #attributes.at("company")])
                  [CUSTOM TITLE: #title]
                  body
                }
                """);
        Files.writeString(templateDir.resolve("footer.typ"), "#let footer-text = [Powered by]\n");
        Path doc = Files.writeString(tempDir.resolve("doc.adoc"), """
                = My Document
                :company: ACME

                TIP: Use a template.
                """);
        File output = tempDir.resolve("doc.pdf").toFile();
        asciidoctor.convertFile(doc.toFile(), Options.builder()
                .backend("pdf")
                .safe(SafeMode.UNSAFE)
                .toFile(output)
                .attributes(Attributes.builder().attribute("pdf-template", "theme/theme.typ").build())
                .build());

        assertThat(text(output.toPath()))
                .contains("CUSTOM TITLE: My Document")
                .contains("ADMONITION TIP: Use a template.")
                .contains("Powered by ACME");
    }

    private static String text(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }
}
