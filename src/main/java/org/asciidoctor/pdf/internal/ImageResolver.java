package org.asciidoctor.pdf.internal;

import org.asciidoctor.ast.ContentNode;

/** Resolves the target of an image to the path that Typst reads. */
@FunctionalInterface
public interface ImageResolver {

    /**
     * @param node the image node (block or inline)
     * @param target the target of the image macro
     * @return the path of the image for Typst, or {@code null} if the image is not available
     */
    String resolve(ContentNode node, String target);
}
