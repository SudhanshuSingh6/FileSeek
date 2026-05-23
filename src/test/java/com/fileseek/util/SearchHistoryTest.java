package com.fileseek.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchHistoryTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Redirect to temp directory for test isolation
        // Requires package-private: static Path historyFile = ...
        SearchHistory.HistoryFile = tempDir.resolve("history.txt");
    }

    @AfterEach
    void tearDown() {
        SearchHistory.clear();
    }

    @Test
    void appendCreatesFileOnFirstWrite() {
        SearchHistory.append("redis caching");
        assertTrue(Files.exists(SearchHistory.HistoryFile));
    }

    @Test
    void appendedQueryAppearsInRead() {
        SearchHistory.append("spring boot");
        List<String> entries = SearchHistory.read(10);
        assertFalse(entries.isEmpty());
        assertTrue(entries.get(0).contains("spring boot"));
    }

    @Test
    void readReturnsMostRecentFirst() {
        SearchHistory.append("first query");
        SearchHistory.append("second query");
        SearchHistory.append("third query");

        List<String> entries = SearchHistory.read(10);

        assertTrue(entries.get(0).contains("third query"), "most recent must be first");
        assertTrue(entries.get(1).contains("second query"));
        assertTrue(entries.get(2).contains("first query"));
    }

    @Test
    void readRespectsLimit() {
        for (int i = 0; i < 10; i++) {
            SearchHistory.append("query " + i);
        }
        List<String> entries = SearchHistory.read(3);
        assertEquals(3, entries.size());
    }

    @Test
    void readReturnsEmptyWhenNoHistory() {
        List<String> entries = SearchHistory.read(20);
        assertTrue(entries.isEmpty());
    }

    @Test
    void appendIgnoresBlankQuery() {
        SearchHistory.append("   ");
        SearchHistory.append("");
        SearchHistory.append(null);
        assertTrue(SearchHistory.read(10).isEmpty());
    }

    @Test
    void clearDeletesHistoryFile() {
        SearchHistory.append("redis");
        assertTrue(Files.exists(SearchHistory.HistoryFile));
        SearchHistory.clear();
        assertFalse(Files.exists(SearchHistory.HistoryFile));
    }

    @Test
    void clearOnNonExistentFileDoesNotThrow() {
        assertDoesNotThrow(SearchHistory::clear);
    }

    @Test
    void multipleAppendsAccumulate() {
        SearchHistory.append("redis");
        SearchHistory.append("spring boot");
        SearchHistory.append("docker");

        List<String> entries = SearchHistory.read(10);
        assertEquals(3, entries.size());
    }

    @Test
    void entryContainsTimestamp() {
        SearchHistory.append("redis");
        List<String> entries = SearchHistory.read(1);
        // Format: yyyy-MM-dd'T'HH:mm:ss\tquery
        assertTrue(entries.get(0).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\t.*"),
                "Entry must begin with ISO-8601 timestamp");
    }

    @Test
    void appendIsNonFatalOnIOError() {
        // Point to an invalid path
        SearchHistory.HistoryFile = tempDir.resolve("not-a-dir/history.txt");
        assertDoesNotThrow(() -> SearchHistory.append("test query"));
    }

    @Test
    void preservesQueryExactly() {
        String query = "redis --ext .java --modified-after 7d";
        SearchHistory.append(query);
        List<String> entries = SearchHistory.read(1);
        assertTrue(entries.get(0).contains(query));
    }
}
