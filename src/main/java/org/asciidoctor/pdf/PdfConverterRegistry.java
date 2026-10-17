package org.asciidoctor.pdf;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.jruby.converter.spi.ConverterRegistry;

/** Registers the {@code pdf} and {@code typst} backends. */
public class PdfConverterRegistry implements ConverterRegistry {

    @Override
    public void register(Asciidoctor asciidoctor) {
        asciidoctor.javaConverterRegistry().register(PdfConverter.class);
        asciidoctor.javaConverterRegistry().register(TypstConverter.class);
    }
}
