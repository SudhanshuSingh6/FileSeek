package com.fileseek.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

@Command(
        name = "search",
        mixinStandardHelpOptions = true,
        description = "Search indexed files by content, filename, or folder."
)
public class SearchCommand implements Runnable {

    @Parameters(index = "0", description = "Search query.")
    private String query;

    @Option(names = "--fuzzy",  description = "Enable fuzzy (typo-tolerant) matching.")
    private boolean fuzzy;

    @Option(names = "--prefix", description = "Enable prefix/autocomplete matching.")
    private boolean prefix;

    @Option(names = "--ext",    description = "Filter by file extension (e.g. .java).")
    private String extension;

    @Option(names = "--min-size", description = "Filter by minimum file size (e.g. 1MB).")
    private String minSize;

    @Option(names = "--modified-after", description = "Filter by modification date (e.g. 7d).")
    private String modifiedAfter;

    @Override
    public void run() {
        // Phase 7 — stub only
        System.out.printf("search: query=\"%s\" fuzzy=%b prefix=%b%n", query, fuzzy, prefix);
    }
}