package com.fileseek.util;

import com.fileseek.config.ConfigManager;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public final class SearchHistory {

    private static final Path HISTORY_FILE =
            ConfigManager.getConfigDirPath().resolve("history.txt");

    private static final int MAX_ENTRIES = 500;
    private static final String SEPARATOR = "\t";
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private SearchHistory() {
    }

    public static void append(String query) {
        if (query == null || query.isBlank()) return;
        try {
            Files.createDirectories(HISTORY_FILE.getParent());
            String entry = LocalDateTime.now().format(FMT)
                    + SEPARATOR + query.trim() + System.lineSeparator();
            Files.writeString(HISTORY_FILE, entry,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            trimIfNeeded();
        } catch (IOException ignored) {
        }
    }

    public static List<String> read(int limit) {
        if (!Files.exists(HISTORY_FILE)) return List.of();
        try {
            List<String> lines = Files.readAllLines(HISTORY_FILE);
            Collections.reverse(lines);
            return lines.stream()
                    .filter(l -> !l.isBlank())
                    .limit(limit)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(HISTORY_FILE);
        } catch (IOException ignored) {
        }
    }


    private static void trimIfNeeded() throws IOException {
        List<String> lines = Files.readAllLines(HISTORY_FILE);
        if (lines.size() <= MAX_ENTRIES) return;
        List<String> trimmed = lines.subList(lines.size() - MAX_ENTRIES, lines.size());
        Files.write(HISTORY_FILE, trimmed);
    }
}