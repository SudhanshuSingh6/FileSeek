package com.fileseek.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FileParserTest {

    @TempDir
    Path tempDir;

    private final FileParser parser = new FileParser();

    @Test
    void parsesTxtFile() throws IOException {
        Optional<String> result = parser.parse(write("test.txt", "redis caching"));
        assertTrue(result.isPresent());
        assertTrue(result.get().contains("redis caching"));
    }

    @Test
    void parsesMdFile() throws IOException {
        Optional<String> result = parser.parse(write("readme.md", "# Spring Boot"));
        assertTrue(result.isPresent());
    }

    @Test
    void parsesJavaFile() throws IOException {
        Optional<String> result = parser.parse(
                write("Main.java", "public class Main { }"));
        assertTrue(result.isPresent());
    }

    @Test
    void parsesJsonFile() throws IOException {
        Optional<String> result = parser.parse(
                write("config.json", "{\"key\": \"value\"}"));
        assertTrue(result.isPresent());
    }

    @Test
    void parsesXmlFile() throws IOException {
        Optional<String> result = parser.parse(
                write("pom.xml", "<project><name>test</name></project>"));
        assertTrue(result.isPresent());
    }

    @Test
    void parsesYmlFile() throws IOException {
        Optional<String> result = parser.parse(
                write("app.yml", "server:\n  port: 8080"));
        assertTrue(result.isPresent());
    }

    @Test
    void parsesPropertiesFile() throws IOException {
        Optional<String> result = parser.parse(
                write("app.properties", "spring.port=8080"));
        assertTrue(result.isPresent());
    }

    @Test
    void returnsEmptyForUnsupportedExtension() throws IOException {
        Optional<String> result = parser.parse(write("image.png", "binary"));
        assertFalse(result.isPresent());
    }

    @Test
    void returnsEmptyForExeExtension() throws IOException {
        Optional<String> result = parser.parse(write("app.exe", "MZ"));
        assertFalse(result.isPresent());
    }

    @Test
    void returnsEmptyForNoExtension() throws IOException {
        Optional<String> result = parser.parse(write("Makefile", "all: build"));
        assertFalse(result.isPresent());
    }

    @Test
    void isSupportedReturnsTrueForAllTextExtensions() throws IOException {
        String[] exts = {".txt", ".md", ".java", ".json", ".xml", ".yml", ".properties"};
        for (String ext : exts) {
            Path file = write("file" + ext, "content");
            assertTrue(parser.isSupported(file), "Expected supported: " + ext);
        }
    }

    @Test
    void isSupportedReturnsTrueForPdf() throws IOException {
        assertTrue(parser.isSupported(write("doc.pdf", "dummy")));
    }

    @Test
    void isSupportedReturnsFalseForBinary() throws IOException {
        assertFalse(parser.isSupported(write("binary.exe", "MZ")));
    }

    @Test
    void extensionExtractorLowercases() throws IOException {
        assertEquals(".txt", FileParser.extension(write("Report.TXT", "content")));
    }

    @Test
    void extensionExtractorReturnsEmptyForNoExtension() throws IOException {
        assertEquals("", FileParser.extension(write("Makefile", "content")));
    }

    @Test
    void extensionExtractorHandlesMultipleDots() throws IOException {
        // Only the last extension counts
        assertEquals(".txt",
                FileParser.extension(write("archive.tar.txt", "content")));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void parseFallsBackGracefullyOnUnreadableFile() throws IOException {
        Path file = write("locked.txt", "content");
        file.toFile().setReadable(false);
        try {
            Optional<String> result = parser.parse(file);
            assertTrue(result.isEmpty(),
                    "Unreadable file must return empty rather than throw");
        } finally {
            file.toFile().setReadable(true);
        }
    }

    // --- helper ---

    private Path write(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
