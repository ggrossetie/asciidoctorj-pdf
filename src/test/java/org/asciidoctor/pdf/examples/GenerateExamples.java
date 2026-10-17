package org.asciidoctor.pdf.examples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.Attributes;
import org.asciidoctor.Options;
import org.asciidoctor.SafeMode;

/**
 * Converts the AsciiDoc files of the example directory to PDF, next to them (mvn -Pexamples package).
 *
 * <p>The PDF files are reproducible: they don't change unless the documents, the templates or the converter change.
 */
public final class GenerateExamples {

    private GenerateExamples() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "example");
        List<Path> documents;
        try (Stream<Path> paths = Files.walk(dir)) {
            documents = paths.filter(path -> path.toString().endsWith(".adoc")).sorted().toList();
        }
        try (Asciidoctor asciidoctor = Asciidoctor.Factory.create()) {
            for (Path document : documents) {
                asciidoctor.convertFile(document.toFile(), Options.builder()
                        .backend("pdf")
                        .safe(SafeMode.UNSAFE)
                        .attributes(Attributes.builder().attribute("reproducible").build())
                        .build());
                System.out.println("Converted " + document);
            }
        }
    }
}
