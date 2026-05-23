package com.fileseek.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TextParserTest {

    @TempDir
    Path tempDir;

    private final TextParser parser = new TextParser();

    @Test
    void parsesUtf8File() throws IOException {
        Path file = write("utf8.txt", "Redis caching improves performance");
        String result = parser.parse(file);
        assertEquals("Redis caching improves performance", result.trim());
    }

    @Test
    void parsesMultilineFile() throws IOException {
        Path file = write("multi.txt", "line one\nline two\nline three");
        String result = parser.parse(file);
        assertTrue(result.contains("line one"));
        assertTrue(result.contains("line two"));
        assertTrue(result.contains("line three"));
    }

    @Test
    void parsesEmptyFile() throws IOException {
        Path file = write("empty.txt", "");
        String result = parser.parse(file);
        assertEquals("", result);
    }

    @Test
    void fallsBackToIso8859ForNonUtf8() throws IOException {
        Path file = tempDir.resolve("latin1.txt");
        // Bytes valid in ISO-8859-1 but invalid in UTF-8
        byte[] latin1 = new byte[]{(byte) 0xE9, (byte) 0xE0, (byte) 0xFC};
        Files.write(file, latin1);

        String result = assertDoesNotThrow(() -> parser.parse(file));
        assertNotNull(result);
    }

    @Test
    void parsesLargeFile() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            sb.append("redis caching line ").append(i).append("\n");
        }
        Path file = write("large.txt", sb.toString());
        String result = parser.parse(file);
        assertTrue(result.contains("redis caching line 0"));
        assertTrue(result.contains("redis caching line 9999"));
    }

    @Test
    void preservesContent() throws IOException {
        String content = "spring.datasource.url=jdbc:postgresql://localhost/mydb";
        Path file = write("app.properties", content);
        String result = parser.parse(file);
        assertEquals(content, result.trim());
    }

    @Test
    void parsesJavaSourceFile() throws IOException {
        String java = "public class Main {\n    public static void main(String[] args) {}\n}";
        Path file = write("Main.java", java);
        String result = parser.parse(file);
        assertTrue(result.contains("public class Main"));
    }

    @Test
    void parsesJsonFile() throws IOException {
        String json = "{\"key\": \"redis\", \"value\": \"caching\"}";
        Path file = write("config.json", json);
        String result = parser.parse(file);
        assertTrue(result.contains("redis"));
    }

    @Test
    void parsesMarkdownFile() throws IOException {
        String md = "# Redis Guide\n\nRedis is an in-memory data structure store.";
        Path file = write("guide.md", md);
        String result = parser.parse(file);
        assertTrue(result.contains("Redis Guide"));
    }

    // --- helper ---

    private Path write(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
