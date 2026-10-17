package org.asciidoctor.pdf.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The base template ({@code asciidoctor.typ}): the default rendering of the AsciiDoc elements. */
public final class BaseTemplate {

    private static final String SOURCE = load();

    private BaseTemplate() {
    }

    public static String source() {
        return SOURCE;
    }

    private static String load() {
        try (InputStream in = BaseTemplate.class.getResourceAsStream("/org/asciidoctor/pdf/" + TypstTranslator.BASE_TEMPLATE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + TypstTranslator.BASE_TEMPLATE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
