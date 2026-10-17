package org.asciidoctor.pdf;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.asciidoctor.ast.ContentNode;
import org.asciidoctor.ast.Document;
import org.asciidoctor.converter.AbstractConverter;
import org.asciidoctor.converter.ConverterFor;
import org.asciidoctor.log.LogRecord;
import org.asciidoctor.log.Severity;
import org.asciidoctor.pdf.internal.BaseTemplate;
import org.asciidoctor.pdf.internal.TypstTranslator;

import fr.yuzutech.typst.pdf.CompileResult;
import fr.yuzutech.typst.pdf.Diagnostic;
import fr.yuzutech.typst.pdf.TypstCompiler;

/**
 * Converts AsciiDoc to PDF (backend {@code pdf}): the document is converted to Typst, then compiled to PDF by Typst
 * (compiled to WebAssembly, then to JVM bytecode with Chicory).
 *
 * <p>Attributes:
 * <ul>
 * <li>{@code pdf-template}: the Typst template, relative to the document directory. Its directory is available to
 * Typst as {@code /template/}, and the base template as {@code /asciidoctor.typ}.</li>
 * <li>{@code pdf-fontsdir}: the directories (separated by a comma or a semicolon) of additional fonts.</li>
 * <li>{@code reproducible}: don't set the creation date of the PDF.</li>
 * </ul>
 */
@ConverterFor(value = PdfConverter.BACKEND, suffix = ".pdf")
public class PdfConverter extends AbstractConverter<Object> {

    public static final String BACKEND = "pdf";

    private static final String MAIN = "/main.typ";
    private static final String TEMPLATE_DIR = "/template/";
    private static final String ASSETS_DIR = "/assets/";
    private static final int TEMPLATE_DIR_DEPTH = 4;
    private static final Set<String> FONT_EXTENSIONS = Set.of("ttf", "otf", "ttc", "otc");
    private static final Set<String> TEMPLATE_EXTENSIONS = Set.of("typ", "png", "jpg", "jpeg", "gif", "svg", "webp",
            "pdf", "json", "yaml", "yml", "toml", "csv", "xml", "txt", "bib", "csl");

    /** Creating a compiler is costly, and it keeps the incremental cache of Typst: it's shared. */
    private static TypstCompiler sharedCompiler;

    /** The files of the Typst project, by path. */
    private final Map<String, byte[]> files = new LinkedHashMap<>();
    /** The virtual path of the image files, by file. */
    private final Map<Path, String> images = new LinkedHashMap<>();
    private final List<byte[]> fonts = new ArrayList<>();
    private TypstTranslator translator;
    /** The depth of the documents being converted: a nested document (AsciiDoc table cell) is part of its parent. */
    private int depth;

    public PdfConverter(String backend, Map<String, Object> opts) {
        super(backend, opts);
    }

    @Override
    public Object convert(ContentNode node, String transform, Map<Object, Object> opts) {
        if (!(node instanceof Document doc) || depth > 0) {
            return translator.convert(node, transform);
        }
        depth++;
        try {
            return convertDocument(doc);
        } finally {
            depth--;
        }
    }

    private byte[] convertDocument(Document doc) {
        files.clear();
        images.clear();
        fonts.clear();
        Path docdir = docdir(doc);
        String templateImport = null;
        Object template = doc.getAttribute("pdf-template");
        if (template != null) {
            Path templateFile = docdir.resolve(String.valueOf(template)).normalize();
            if (!Files.isRegularFile(templateFile)) {
                throw new IllegalArgumentException("pdf-template not found: " + templateFile);
            }
            addTemplateDirectory(templateFile.getParent());
            templateImport = TEMPLATE_DIR + templateFile.getFileName();
        }
        Object fontsdir = doc.getAttribute("pdf-fontsdir");
        if (fontsdir != null) {
            for (String dir : String.valueOf(fontsdir).split("[,;]")) {
                if (!dir.isBlank()) {
                    addFonts(docdir.resolve(dir.trim()));
                }
            }
        }
        translator = new TypstTranslator(this::resolveImage, "/" + TypstTranslator.BASE_TEMPLATE, templateImport);
        String source = translator.convert(doc, "document");
        files.put("/" + TypstTranslator.BASE_TEMPLATE, BaseTemplate.source().getBytes(StandardCharsets.UTF_8));
        files.put(MAIN, source.getBytes(StandardCharsets.UTF_8));
        return compile(doc, source);
    }

    private byte[] compile(Document doc, String source) {
        CompileResult result;
        synchronized (PdfConverter.class) {
            TypstCompiler compiler = compiler();
            compiler.reset();
            files.forEach(compiler::addFile);
            fonts.forEach(compiler::addFont);
            if (!doc.hasAttribute("reproducible")) {
                OffsetDateTime now = OffsetDateTime.now();
                compiler.setNow(now.toInstant(), now.getOffset());
            }
            result = compiler.compile(MAIN);
        }
        for (Diagnostic warning : result.warnings()) {
            log(new LogRecord(Severity.WARN, "typst: " + describe(warning, source)));
        }
        if (!result.isSuccess()) {
            StringBuilder message = new StringBuilder("Typst compilation failed:");
            for (Diagnostic error : result.errors()) {
                log(new LogRecord(Severity.ERROR, "typst: " + describe(error, source)));
                message.append("\n  ").append(describe(error, source));
            }
            throw new IllegalStateException(message.toString());
        }
        return result.pdf();
    }

    private static synchronized TypstCompiler compiler() {
        if (sharedCompiler == null) {
            sharedCompiler = TypstCompiler.create();
        }
        return sharedCompiler;
    }

    /** Describes a diagnostic, with the line of the generated Typst source it refers to. */
    private static String describe(Diagnostic diagnostic, String source) {
        StringBuilder out = new StringBuilder(diagnostic.toString());
        if (diagnostic.path().map(MAIN::equals).orElse(false) && diagnostic.line() > 0) {
            String[] lines = source.split("\n", -1);
            if (diagnostic.line() <= lines.length) {
                out.append("\n    | ").append(lines[diagnostic.line() - 1].strip());
            }
        }
        return out.toString();
    }

    @Override
    public void write(Object output, OutputStream out) throws IOException {
        if (output instanceof byte[] bytes) {
            out.write(bytes);
        } else if (output != null) {
            out.write(String.valueOf(output).getBytes(StandardCharsets.UTF_8));
        }
    }

    // Files of the Typst project

    private String resolveImage(ContentNode node, String target) {
        if (target.startsWith("data:")) {
            int comma = target.indexOf(',');
            int slash = target.indexOf('/');
            if (comma < 0 || !target.substring(0, comma).endsWith(";base64")) {
                return null;
            }
            String extension = slash > 0 ? target.substring(slash + 1, target.indexOf(';')) : "png";
            String path = ASSETS_DIR + "data-" + files.size() + "." + extension.replace("svg+xml", "svg");
            files.put(path, Base64.getMimeDecoder().decode(target.substring(comma + 1)));
            return path;
        }
        if (target.contains("://")) {
            log(new LogRecord(Severity.WARN, "remote image not supported: " + target));
            return null;
        }
        Path file = docdir(node.getDocument()).resolve(node.imageUri(target)).normalize();
        String path = images.get(file);
        if (path != null) {
            return path;
        }
        if (!Files.isRegularFile(file)) {
            log(new LogRecord(Severity.WARN, "image to embed not found or not readable: " + file));
            return null;
        }
        path = ASSETS_DIR + images.size() + "-" + file.getFileName();
        try {
            files.put(path, Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        images.put(file, path);
        return path;
    }

    private void addTemplateDirectory(Path dir) {
        try (Stream<Path> paths = Files.walk(dir, TEMPLATE_DIR_DEPTH)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) {
                String extension = extension(file);
                if (TEMPLATE_EXTENSIONS.contains(extension)) {
                    files.put(TEMPLATE_DIR + dir.relativize(file).toString().replace('\\', '/'),
                            Files.readAllBytes(file));
                } else if (FONT_EXTENSIONS.contains(extension)) {
                    fonts.add(Files.readAllBytes(file));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addFonts(Path dir) {
        if (!Files.isDirectory(dir)) {
            log(new LogRecord(Severity.WARN, "pdf-fontsdir not found: " + dir));
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path file : paths.filter(f -> FONT_EXTENSIONS.contains(extension(f))).toList()) {
                fonts.add(Files.readAllBytes(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path docdir(Document doc) {
        Object docdir = doc.getAttribute("docdir");
        return Path.of(docdir == null || String.valueOf(docdir).isEmpty() ? "" : String.valueOf(docdir))
                .toAbsolutePath();
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
