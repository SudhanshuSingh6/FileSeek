package com.fileseek.scanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public class FileParser {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".md", ".java", ".json", ".xml",
            ".yml", ".yaml", ".properties"
    );

    private final TextParser textParser = new TextParser();
    private final PdfParser pdfParser = new PdfParser();

    public Optional<String> parse(Path file) {
        String ext = extension(file);

        try {
            if (TEXT_EXTENSIONS.contains(ext)) {
                return Optional.of(textParser.parse(file));
            }
            if (ext.equals(".pdf")) {
                return Optional.of(pdfParser.parse(file));
            }
        } catch (IOException e) {
            System.err.printf("[warn] Could not parse %s: %s%n",
                    file.getFileName(), e.getMessage());
        }

        return Optional.empty();
    }

    public boolean isSupported(Path file) {
        String ext = extension(file);
        return TEXT_EXTENSIONS.contains(ext) || ext.equals(".pdf");
    }

    public static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(dot).toLowerCase() : "";
    }
}