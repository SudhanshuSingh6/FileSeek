package com.fileseek.cli;

import com.fileseek.util.SearchHistory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "history",
        mixinStandardHelpOptions = true,
        description = "Show recent search queries."
)
public class HistoryCommand implements Callable<Integer> {

    private static final String ANSI_DIM = "\u001B[2m";
    private static final String ANSI_RESET = "\u001B[0m";

    @Option(names = {"-n", "--limit"},
            description = "Number of entries to show (default: 20).",
            defaultValue = "20")
    private int limit;

    @Option(names = "--clear",
            description = "Clear all search history.")
    private boolean clear;

    @Override
    public Integer call() {
        if (clear) {
            SearchHistory.clear();
            System.out.println("Search history cleared.");
            return 0;
        }

        List<String> entries = SearchHistory.read(limit);

        if (entries.isEmpty()) {
            System.out.println("No search history yet.");
            System.out.println("Run 'fileseek search <query>' to get started.");
            return 0;
        }

        System.out.println();
        System.out.printf("Last %d searches:%n%n", entries.size());

        for (int i = 0; i < entries.size(); i++) {
            String line = entries.get(i);
            int tab = line.indexOf('\t');
            if (tab > 0) {
                String timestamp = line.substring(0, tab);
                String query = line.substring(tab + 1);
                System.out.printf(
                        "  %s%2d.%s  %s%s%s  %s%n",
                        ANSI_DIM, i + 1, ANSI_RESET,
                        ANSI_DIM, timestamp, ANSI_RESET,
                        query
                );
            } else {
                System.out.printf("  %2d.  %s%n", i + 1, line);
            }
        }

        System.out.println();
        return 0;
    }
}