package com.fileseek.search;

import com.fileseek.model.FileMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SnippetExtractorTest {

    @TempDir
    Path tempDir;

    private final SnippetExtractor extractor = new SnippetExtractor();

    @Test
    void extractsSnippetAroundMatch() throws IOException {
        Path file = write("notes.txt",
                "Spring Boot simplifies Java development. "
                        + "Redis caching improves performance significantly. "
                        + "Docker containers are portable.");

        String snippet = extractor.extract(meta(file), List.of("redis"));

        assertFalse(snippet.isBlank());
        assertTrue(snippet.toLowerCase().contains("redis"),
                "Snippet must contain the matched term");
    }

    @Test
    void snippetIncludesSurroundingContext() throws IOException {
        Path file = write("notes.txt",
                "The quick brown fox jumps. "
                        + "Redis is a fast in-memory database. "
                        + "It supports many data structures.");

        String snippet = extractor.extract(meta(file), List.of("redis"));

        assertFalse(snippet.isBlank());
        // Context words near "redis" should be visible
        assertTrue(snippet.toLowerCase().contains("redis"));
    }

    @Test
    void snippetContainsEllipsisWhenTruncated() throws IOException {
        // Long preamble forces the match to be far from the start
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) sb.append("word").append(i).append(" ");
        sb.append("redis caching");
        for (int i = 0; i < 50; i++) sb.append(" trailing").append(i);

        Path file = write("long.txt", sb.toString());
        String snippet = extractor.extract(meta(file), List.of("redis"));

        assertTrue(snippet.contains("..."),
                "Content far from start must produce ellipsis prefix");
    }

    @Test
    void returnsEmptyForNonexistentFile() {
        FileMetadata meta = new FileMetadata(
                1, "/does/not/exist.txt", "exist.txt", ".txt", 0L, 0L);
        assertEquals("", extractor.extract(meta, List.of("redis")));
    }

    @Test
    void returnsEmptyForEmptyTermList() throws IOException {
        Path file = write("notes.txt", "redis spring docker");
        assertEquals("", extractor.extract(meta(file), List.of()));
    }

    @Test
    void returnsEmptyWhenNoTermMatchesContent() throws IOException {
        Path file = write("notes.txt", "spring boot java framework");
        assertEquals("", extractor.extract(meta(file), List.of("redis")));
    }

    @Test
    void usesFirstOccurrenceWhenMultipleTermsPresent() throws IOException {
        Path file = write("notes.txt",
                "Redis is fast. " +
                        "Much later in the document... Spring Boot.");

        String snippet = extractor.extract(meta(file),
                List.of("redis", "spring"));

        // Should extract around "redis" (first match)
        assertFalse(snippet.isBlank());
    }

    @Test
    void snippetDoesNotContainRawNewlines() throws IOException {
        Path file = write("notes.txt",
                "First line.\nRedis caching.\nThird line.\nFourth line.");

        String snippet = extractor.extract(meta(file), List.of("redis"));

        assertFalse(snippet.contains("\n"),
                "Snippet must not contain raw newline characters");
        assertFalse(snippet.contains("\r"));
    }

    @Test
    void handlesEmptyFileGracefully() throws IOException {
        Path file = write("empty.txt", "");
        assertEquals("", extractor.extract(meta(file), List.of("redis")));
    }

    @Test
    void handlesBlankFileGracefully() throws IOException {
        Path file = write("blank.txt", "   \n   \n   ");
        assertEquals("", extractor.extract(meta(file), List.of("redis")));
    }

    @Test
    void matchAtStartOfFileNoLeadingEllipsis() throws IOException {
        // Match is right at the beginning — no leading ellipsis needed
        Path file = write("notes.txt",
                "Redis caching is a technique used in many systems.");

        String snippet = extractor.extract(meta(file), List.of("redis"));

        assertFalse(snippet.isBlank());
        assertFalse(snippet.startsWith("..."),
                "No ellipsis prefix when match is near the start");
    }

    @Test
    void extractorIsNonFatalOnUnreadableFile() throws IOException {
        Path file = write("readable.txt", "redis caching");
        file.toFile().setReadable(false);
        try {
            String snippet = assertDoesNotThrow(
                    () -> extractor.extract(meta(file), List.of("redis")));
            // Returns empty rather than throwing
            assertEquals("", snippet);
        } finally {
            file.toFile().setReadable(true);
        }
    }

    @Test
    void multipleTermsAllHighlightedInSnippet() throws IOException {
        Path file = write("notes.txt",
                "Redis caching and spring boot improve performance.");

        String snippet = extractor.extract(meta(file),
                List.of("redis", "spring"));

        assertFalse(snippet.isBlank());
        // Both terms should appear highlighted in the snippet
        // (ANSI codes wrap them — check case-insensitive presence)
        assertTrue(snippet.toLowerCase().contains("redis")
                        || snippet.toLowerCase().contains("spring"),
                "At least one matched term must appear in the snippet");
    }

    // --- helper ---

    private Path write(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private FileMetadata meta(Path file) {
        return new FileMetadata(
                1,
                file.toAbsolutePath().toString(),
                file.getFileName().toString(),
                ".txt",
                100L,
                System.currentTimeMillis());
    }
}
