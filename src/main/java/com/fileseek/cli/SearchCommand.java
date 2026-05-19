package com.fileseek.cli;

import com.fileseek.cli.display.Spinner;
import com.fileseek.index.IndexManager;
import com.fileseek.model.FileMetadata;
import com.fileseek.model.QueryOptions;
import com.fileseek.model.SearchResult;
import com.fileseek.search.SearchEngine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(
        name = "search",
        mixinStandardHelpOptions = true,
        description = "Search indexed files by content, filename, or folder."
)
public class SearchCommand implements Runnable {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_DIM = "\u001B[2m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";

    @Parameters(index = "0", description = "Search query.")
    private String query;

    @Option(names = "--fuzzy",
            description = "Enable fuzzy (typo-tolerant) matching.")
    private boolean fuzzy;

    @Option(names = "--prefix",
            description = "Enable prefix/autocomplete matching.")
    private boolean prefix;

    @Option(names = "--ext",
            description = "Filter by file extension (e.g. .java).")
    private String extension;

    @Option(names = "--min-size",
            description = "Filter by minimum file size (e.g. 1MB, 500KB).")
    private String minSize;

    @Option(names = "--modified-after",
            description = "Filter files modified within duration (e.g. 7d, 30d).")
    private String modifiedAfter;

    @Override
    public void run() {
        if (!IndexManager.indexExists()) {
            System.out.printf("%sNo index found.%s Run 'fileseek add <directory>' first.%n",
                    ANSI_YELLOW, ANSI_RESET);
            return;
        }

        IndexManager indexManager = new IndexManager();
        indexManager.load();

        if (indexManager.documentCount() == 0) {
            System.out.printf("%sIndex is empty.%s Run 'fileseek add <directory>' first.%n",
                    ANSI_YELLOW, ANSI_RESET);
            return;
        }

        QueryOptions options = buildOptions();
        SearchEngine engine = new SearchEngine(indexManager);

        Spinner spinner = new Spinner("Searching");
        spinner.start();
        List<SearchResult> results = engine.search(options);
        spinner.stop();

        printResults(results);
    }

    private void printResults(List<SearchResult> results) {
        System.out.println();

        if (results.isEmpty()) {
            System.out.printf("%sNo results%s found for \"%s\"%n%n",
                    ANSI_YELLOW, ANSI_RESET, query);
            printSearchTips();
            return;
        }

        long durationMs = results.get(0).getSearchDurationMs();
        System.out.printf("%sFound %,d result%s%s for \"%s\" %s(%dms)%s%n%n",
                ANSI_BOLD, results.size(),
                results.size() == 1 ? "" : "s",
                ANSI_RESET,
                query,
                ANSI_DIM, durationMs, ANSI_RESET);

        for (int i = 0; i < results.size(); i++) {
            printResult(i + 1, results.get(i));
        }

        System.out.printf("%s%,d result%s in %dms  |  index: %,d documents%s%n%n",
                ANSI_DIM,
                results.size(),
                results.size() == 1 ? "" : "s",
                results.get(0).getSearchDurationMs(),
                results.size(),
                ANSI_RESET);
    }

    private void printResult(int rank, SearchResult result) {
        FileMetadata meta = result.getMetadata();

        System.out.printf("%s[%d] %s%s%n",
                ANSI_BOLD, rank, meta.getFileName(), ANSI_RESET);

        System.out.printf("    %s%s%s%n",
                ANSI_DIM, meta.getPath(), ANSI_RESET);

        System.out.printf("    %s%s  ·  %s  ·  modified %s  ·  score %.4f%s%n",
                ANSI_DIM,
                meta.getExtension().isEmpty() ? "no ext" : meta.getExtension(),
                formatSize(meta.getSizeBytes()),

                formatAge(meta.getLastModified()),
                result.getScore(),
                ANSI_RESET);

        if (!result.getSnippet().isBlank()) {
            System.out.printf("    %s\"%s\"%s%n",
                    ANSI_CYAN, result.getSnippet(), ANSI_RESET);
        }

        System.out.println();
    }

    private void printSearchTips() {
        System.out.println("Tips:");
        System.out.println("  fileseek search \"term\" --fuzzy    typo-tolerant search");
        System.out.println("  fileseek search \"term\" --prefix   prefix/autocomplete");
        System.out.println("  fileseek search \"\\\"exact phrase\\\"\" phrase search");
        System.out.println("  fileseek search \"term\" --ext .java  filter by extension");
        System.out.println();
    }


    private QueryOptions buildOptions() {
        return QueryOptions.builder(query)
                .fuzzy(fuzzy)
                .prefix(prefix)
                .phrase(isPhrase(query))
                .filterExt(extension)
                .minSizeBytes(parseSize(minSize))
                .modifiedAfterEpoch(parseDuration(modifiedAfter))
                .build();
    }

    private boolean isPhrase(String q) {
        return q != null && q.startsWith("\"") && q.endsWith("\"") && q.length() > 2;
    }

    private Long parseSize(String s) {
        if (s == null || s.isBlank()) return null;
        String u = s.toUpperCase().trim();
        try {
            if (u.endsWith("GB")) return Long.parseLong(u.replace("GB", "")) << 30;
            if (u.endsWith("MB")) return Long.parseLong(u.replace("MB", "")) << 20;
            if (u.endsWith("KB")) return Long.parseLong(u.replace("KB", "")) << 10;
            return Long.parseLong(u);
        } catch (NumberFormatException e) {
            System.err.printf("%s[warn]%s Could not parse size: %s%n",
                    ANSI_YELLOW, ANSI_RESET, s);
            return null;
        }
    }

    private Long parseDuration(String s) {
        if (s == null || s.isBlank()) return null;
        String lower = s.toLowerCase().trim();
        try {
            if (lower.endsWith("d")) {
                long days = Long.parseLong(lower.replace("d", ""));
                return System.currentTimeMillis() - days * 86_400_000L;
            }
            if (lower.endsWith("h")) {
                long hours = Long.parseLong(lower.replace("h", ""));
                return System.currentTimeMillis() - hours * 3_600_000L;
            }
        } catch (NumberFormatException e) {
            System.err.printf("%s[warn]%s Could not parse duration: %s%n",
                    ANSI_YELLOW, ANSI_RESET, s);
        }
        return null;
    }


    private String formatSize(long bytes) {
        if (bytes >= 1L << 30) return String.format("%.1f GB", bytes / (double) (1L << 30));
        if (bytes >= 1L << 20) return String.format("%.1f MB", bytes / (double) (1L << 20));
        if (bytes >= 1L << 10) return String.format("%.1f KB", bytes / (double) (1L << 10));
        return bytes + " B";
    }

    private String formatAge(long epochMillis) {
        long ageMs = System.currentTimeMillis() - epochMillis;
        long days = ageMs / 86_400_000L;
        long hours = ageMs / 3_600_000L;
        long minutes = ageMs / 60_000L;
        if (days > 0) return days + "d ago";
        if (hours > 0) return hours + "h ago";
        if (minutes > 0) return minutes + "m ago";
        return "just now";
    }
}