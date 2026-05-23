package com.fileseek.util;

import com.fileseek.config.ConfigManager;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public final class SearchHistory {

    private static final int MAX_ENTRIES = 500;
    private static final String SEPARATOR = "\t";
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    static Path HistoryFile =
            ConfigManager.getConfigDirPath().resolve("history.txt");

    private SearchHistory() {
    }

    public static void append(String query) {
        if (query == null || query.isBlank()) return;
        try {
            Files.createDirectories(HistoryFile.getParent());
            String entry = LocalDateTime.now().format(FMT)
                    + SEPARATOR + query.trim() + System.lineSeparator();
            Files.writeString(HistoryFile, entry,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            trimIfNeeded();
        } catch (IOException ignored) {
        }
    }

    public static List<String> read(int limit) {
        if (!Files.exists(HistoryFile)) return List.of();
        try {
            List<String> lines = Files.readAllLines(HistoryFile);
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
            Files.deleteIfExists(HistoryFile);
        } catch (IOException ignored) {
        }
    }


    private static void trimIfNeeded() throws IOException {
        List<String> lines = Files.readAllLines(HistoryFile);
        if (lines.size() <= MAX_ENTRIES) return;
        List<String> trimmed = lines.subList(lines.size() - MAX_ENTRIES, lines.size());
        Files.write(HistoryFile, trimmed);
    }
}