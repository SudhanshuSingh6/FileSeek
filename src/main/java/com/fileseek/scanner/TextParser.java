package com.fileseek.scanner;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.Charset;

public class TextParser {

    public String parse(Path file) throws IOException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            // Fall back for files with non-UTF-8 bytes (latin-1, etc.)
            return Files.readString(file, Charset.forName("ISO-8859-1"));
        }
    }
}