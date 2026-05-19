package com.fileseek.search;

import com.fileseek.model.FileMetadata;
import com.fileseek.scanner.PdfParser;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.List;

public class SnippetExtractor {

    private static final int CONTEXT_CHARS = 100;
    private static final String ANSI_BOLD_YELLOW = "\u001B[1;33m";
    private static final String ANSI_RESET = "\u001B[0m";

    private final PdfParser pdfParser = new PdfParser();

    public String extract(FileMetadata metadata, List<String> terms) {
        if (terms.isEmpty()) return "";

        String content = readContent(metadata);
        if (content == null || content.isBlank()) return "";

        String lower = content.toLowerCase();

        int matchStart = -1;
        String matchedTerm = null;

        for (String term : terms) {
            int pos = lower.indexOf(term);
            if (pos >= 0 && (matchStart < 0 || pos < matchStart)) {
                matchStart = pos;
                matchedTerm = term;
            }
        }

        if (matchStart < 0) return "";

        int snippetStart = Math.max(0, matchStart - CONTEXT_CHARS);
        int snippetEnd = Math.min(content.length(), matchStart + matchedTerm.length() + CONTEXT_CHARS);

        if (snippetStart > 0) {
            int wordBoundary = content.indexOf(' ', snippetStart);
            if (wordBoundary > 0 && wordBoundary < matchStart) {
                snippetStart = wordBoundary + 1;
            }
        }
        if (snippetEnd < content.length()) {
            int wordBoundary = content.lastIndexOf(' ', snippetEnd);
            if (wordBoundary > matchStart) {
                snippetEnd = wordBoundary;
            }
        }

        String snippet = content.substring(snippetStart, snippetEnd)
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();

        String prefix = snippetStart > 0 ? "..." : "";
        String suffix = snippetEnd < content.length() ? "..." : "";

        return prefix + highlight(snippet, terms) + suffix;
    }

    private String readContent(FileMetadata metadata) {
        Path path = Path.of(metadata.getPath());
        if (!Files.exists(path)) return null;

        try {
            if (metadata.getExtension().equalsIgnoreCase(".pdf")) {
                return pdfParser.parse(path);
            }
            try {
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (MalformedInputException e) {
                return Files.readString(path, Charset.forName("ISO-8859-1"));
            }
        } catch (IOException e) {
            return null;
        }
    }

    private String highlight(String snippet, List<String> terms) {
        String result = snippet;
        for (String term : terms) {
            result = result.replaceAll(
                    "(?i)(" + java.util.regex.Pattern.quote(term) + ")",
                    ANSI_BOLD_YELLOW + "$1" + ANSI_RESET
            );
        }
        return result;
    }
}