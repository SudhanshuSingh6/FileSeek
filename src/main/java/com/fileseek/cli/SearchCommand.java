package com.fileseek.cli;

import com.fileseek.index.IndexManager;
import com.fileseek.model.FileMetadata;
import com.fileseek.model.QueryOptions;
import com.fileseek.model.SearchResult;
import com.fileseek.search.SearchEngine;
import com.fileseek.util.SearchHistory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "search",
        mixinStandardHelpOptions = true,
        description = "Search indexed files by content, filename, or folder."
)
public class SearchCommand implements Callable<Integer> {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_DIM = "\u001B[2m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_YELLOW = "\u001B[33m";

    @Parameters(index = "0", description = "Search query. "
            + "Wrap in quotes for phrase search: \"spring boot\"")
    private String query;

    @Option(names = "--fuzzy",
            description = "Typo-tolerant matching (Levenshtein distance ≤ 2).")
    private boolean fuzzy;

    @Option(names = "--prefix",
            description = "Prefix / autocomplete matching.")
    private boolean prefix;

    @Option(names = "--regex",
            description = "Treat query as a regular expression (token-level).")
    private boolean regex;

    @Option(names = "--ext",
            description = "Filter results by file extension, e.g. .java")
    private String extension;

    @Option(names = "--min-size",
            description = "Filter by minimum file size, e.g. 1MB, 500KB")
    private String minSize;

    @Option(names = "--modified-after",
            description = "Filter by modification recency, e.g. 7d, 24h")
    private String modifiedAfter;

    @Override
    public Integer call() {
        if (!IndexManager.indexExists()) {
            System.err.println(
                    "[error] No index found.\n"
                            + "        Run 'fileseek add <directory>' to create one.");
            return 2;
        }

        // load index
        long loadStart = System.currentTimeMillis();
        IndexManager mgr = new IndexManager();
        mgr.load();
        long loadMs = System.currentTimeMillis() - loadStart;

        if (FileSeekCommand.verbose) {
            System.out.printf("  [verbose] Index loaded in %dms — "
                            + "%,d documents, %,d terms%n",
                    loadMs, mgr.documentCount(), mgr.termCount());
        }

        // --- guard: index populated ---
        if (mgr.documentCount() == 0) {
            System.err.println(
                    "[error] Index is empty.\n"
                            + "        Run 'fileseek add <directory>' to index your files.");
            return 2;
        }

        // --- build options ---
        QueryOptions options = buildOptions();

        if (FileSeekCommand.verbose) {
            System.out.printf("  [verbose] Raw query   : \"%s\"%n", query);
            System.out.printf("  [verbose] Mode        : %s%n",
                    detectMode(options));
            if (options.hasExtFilter())
                System.out.printf("  [verbose] Filter ext  : %s%n",
                        options.getFilterExt());
            if (options.hasSizeFilter())
                System.out.printf("  [verbose] Filter size : ≥ %,d bytes%n",
                        options.getMinSizeBytes());
        }

        SearchEngine engine = new SearchEngine(mgr);
        List<SearchResult> results = engine.search(options);

        if (FileSeekCommand.verbose) {
            System.out.printf("  [verbose] Results     : %,d%n", results.size());
            System.out.printf("  [verbose] Duration    : %dms%n",
                    results.isEmpty() ? 0 : results.get(0).getSearchDurationMs());
            System.out.println();
        }

        // record history
        SearchHistory.append(query);

        printResults(results);

        return results.isEmpty() ? 1 : 0;
    }

    // --- display ---

    private void printResults(List<SearchResult> results) {
        System.out.println();

        if (results.isEmpty()) {
            System.out.printf(
                    "%sNo results%s for \"%s\"%n%n", ANSI_YELLOW, ANSI_RESET, query);
            printSearchTips();
            return;
        }

        long durationMs = results.get(0).getSearchDurationMs();
        System.out.printf("%sFound %,d result%s%s for \"%s\" "
                        + "%s(%dms)%s%n%n",
                ANSI_BOLD, results.size(),
                results.size() == 1 ? "" : "s",
                ANSI_RESET, query,
                ANSI_DIM, durationMs, ANSI_RESET);

        for (int i = 0; i < results.size(); i++) {
            printResult(i + 1, results.get(i));
        }

        System.out.printf("%s%,d result%s · %dms%s%n%n",
                ANSI_DIM,
                results.size(), results.size() == 1 ? "" : "s",
                results.get(0).getSearchDurationMs(),
                ANSI_RESET);
    }

    private void printResult(int rank, SearchResult result) {
        FileMetadata meta = result.getMetadata();

        System.out.printf("%s[%d] %s%s%n",
                ANSI_BOLD, rank, meta.getFileName(), ANSI_RESET);
        System.out.printf("    %s%s%s%n",
                ANSI_DIM, meta.getPath(), ANSI_RESET);
        System.out.printf("    %s%s · %s · %s · score %.4f%s%n",
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

        if (FileSeekCommand.verbose) {
            System.out.printf("    %s[verbose] docId=%d  tokens=%d%s%n",
                    ANSI_DIM,
                    meta.getDocId(), meta.getTokenCount(),
                    ANSI_RESET);
        }

        System.out.println();
    }

    private void printSearchTips() {
        System.out.println("Suggestions:");
        System.out.printf("  %-42s typo-tolerant%n",
                "fileseek search \"" + query + "\" --fuzzy");
        System.out.printf("  %-42s prefix match%n",
                "fileseek search \"" + query + "\" --prefix");
        System.out.printf("  %-42s phrase match%n",
                "fileseek search \"\\\"" + query + "\\\"\"");
        System.out.println();
    }

    // --- option parsing ---

    private QueryOptions buildOptions() {
        return QueryOptions.builder(query)
                .fuzzy(fuzzy)
                .prefix(prefix)
                .phrase(isPhrase(query))
                .regex(regex)
                .filterExt(extension)
                .minSizeBytes(parseSize(minSize))
                .modifiedAfterEpoch(parseDuration(modifiedAfter))
                .build();
    }

    private boolean isPhrase(String q) {
        return q != null && q.startsWith("\"") && q.endsWith("\"")
                && q.length() > 2;
    }

    private String detectMode(QueryOptions options) {
        if (options.isRegex()) return "regex";
        if (options.isPhrase()) return "phrase";
        if (options.isFuzzy()) return "fuzzy";
        if (options.isPrefix()) return "prefix";
        return "keyword";
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
            System.err.printf(
                    "[error] Cannot parse size \"%s\". "
                            + "Use a number followed by KB, MB, or GB.%n", s);
            return null;
        }
    }

    private Long parseDuration(String s) {
        if (s == null || s.isBlank()) return null;
        String lower = s.toLowerCase().trim();
        try {
            if (lower.endsWith("d"))
                return System.currentTimeMillis()
                        - Long.parseLong(lower.replace("d", "")) * 86_400_000L;
            if (lower.endsWith("h"))
                return System.currentTimeMillis()
                        - Long.parseLong(lower.replace("h", "")) * 3_600_000L;
            System.err.printf(
                    "[error] Cannot parse duration \"%s\". Use a number followed by d or h.%n", s);
        } catch (NumberFormatException e) {
            System.err.printf(
                    "[error] Cannot parse duration \"%s\". Use a number followed by d or h.%n", s);
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