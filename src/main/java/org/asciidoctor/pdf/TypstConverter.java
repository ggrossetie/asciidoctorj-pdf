package org.asciidoctor.pdf;

import java.util.Map;

import org.asciidoctor.ast.ContentNode;
import org.asciidoctor.converter.ConverterFor;
import org.asciidoctor.converter.StringConverter;
import org.asciidoctor.pdf.internal.TypstTranslator;

/**
 * Converts AsciiDoc to Typst (backend {@code typst}).
 *
 * <p>The output is the Typst source that the {@code pdf} backend compiles. It defines the functions of the base
 * template inline, unless the {@code typst-base} attribute gives the path of a copy of {@code asciidoctor.typ} to
 * import. The {@code pdf-template} attribute is the path of the template to import, relative to the output.
 */
@ConverterFor(value = TypstConverter.BACKEND, suffix = ".typ")
public class TypstConverter extends StringConverter {

    public static final String BACKEND = "typst";

    private TypstTranslator translator;

    public TypstConverter(String backend, Map<String, Object> opts) {
        super(backend, opts);
    }

    @Override
    public String convert(ContentNode node, String transform, Map<Object, Object> opts) {
        if (translator == null) {
            Object base = node.getDocument().getAttribute("typst-base");
            Object template = node.getDocument().getAttribute("pdf-template");
            translator = new TypstTranslator((imageNode, target) -> imageNode.imageUri(target),
                    base == null ? null : String.valueOf(base), template == null ? null : String.valueOf(template));
        }
        return translator.convert(node, transform);
    }
}
