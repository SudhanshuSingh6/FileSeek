package com.fileseek.cli;

import com.fileseek.index.IndexManager;
import com.fileseek.model.FileMetadata;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.*;

@Command(
        name = "stats",
        mixinStandardHelpOptions = true,
        description = "Display index statistics."
)
public class StatsCommand implements Callable<Integer> {

    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_DIM = "\u001B[2m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_RESET = "\u001B[0m";

    private static final int TOP_TERMS = 10;
    private static final int TOP_FILES = 5;

    @Override
    public Integer call() {
        if (!IndexManager.indexExists()) {
            System.out.println("No index found. Run 'fileseek add <directory>' first.");
            return 1;
        }

        System.out.print("Loading index... ");
        IndexManager indexManager = new IndexManager();
        indexManager.load();
        System.out.println("done.");
        System.out.println();

        printHeader("FileSeek Index Statistics");
        printOverview(indexManager);
        printIndexFileInfo();
        printExtensionBreakdown(indexManager);
        printTopTerms(indexManager);
        printLargestFiles(indexManager);

        return 0;
    }


    private void printOverview(IndexManager indexManager) {
        Collection<FileMetadata> docs = indexManager.getDocumentStore()
                .getAllDocuments();

        double avgLen = indexManager.getDocumentStore()
                .averageDocumentLength();

        long totalTokens = docs.stream()
                .mapToLong(FileMetadata::getTokenCount)
                .sum();

        printSection("Overview");
        printRow("Documents", String.format("%,d", indexManager.documentCount()));
        printRow("Unique terms", String.format("%,d", indexManager.termCount()));
        printRow("Total tokens", String.format("%,d", totalTokens));
        printRow("Avg doc length", String.format("%.0f tokens", avgLen));
        System.out.println();
    }

    private void printIndexFileInfo() {
        Path indexFile = IndexManager.getIndexFile();
        printSection("Index File");
        printRow("Location", indexFile.toString());
        try {
            long bytes = Files.size(indexFile);
            printRow("Size on disk", formatSize(bytes));
        } catch (IOException e) {
            printRow("Size on disk", "unavailable");
        }
        System.out.println();
    }

    private void printExtensionBreakdown(IndexManager indexManager) {
        Collection<FileMetadata> docs = indexManager.getDocumentStore()
                .getAllDocuments();
        int total = docs.size();

        Map<String, Long> byExt = docs.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getExtension().isEmpty() ? "(none)" : m.getExtension(),
                        Collectors.counting()));

        printSection("Extension Breakdown");

        byExt.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> {
                    double pct = total > 0 ? (e.getValue() * 100.0 / total) : 0;
                    System.out.printf("  %-14s %s%,6d%s  %s(%.1f%%)%s%n",
                            e.getKey(),
                            ANSI_CYAN, e.getValue(), ANSI_RESET,
                            ANSI_DIM, pct, ANSI_RESET);
                });
        System.out.println();
    }

    private void printTopTerms(IndexManager indexManager) {
        printSection("Top " + TOP_TERMS + " Terms by Document Frequency");

        indexManager.getInvertedIndex().getAllTerms().stream()
                .map(term -> Map.entry(term,
                        indexManager.getInvertedIndex().documentFrequency(term)))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_TERMS)
                .forEach(e -> System.out.printf("  %-20s %s%,d docs%s%n",
                        e.getKey(),
                        ANSI_DIM, e.getValue(), ANSI_RESET));
        System.out.println();
    }

    private void printLargestFiles(IndexManager indexManager) {
        printSection("Top " + TOP_FILES + " Largest Indexed Files");

        indexManager.getDocumentStore().getAllDocuments().stream()
                .sorted(Comparator.comparingLong(FileMetadata::getSizeBytes).reversed())
                .limit(TOP_FILES)
                .forEach(meta -> System.out.printf("  %-12s  %s%s%s%n",
                        formatSize(meta.getSizeBytes()),
                        ANSI_DIM, meta.getPath(), ANSI_RESET));
        System.out.println();
    }

    // --- formatting helpers ---

    private void printHeader(String title) {
        System.out.println(ANSI_BOLD + title + ANSI_RESET);
        System.out.println("=".repeat(title.length()));
        System.out.println();
    }

    private void printSection(String title) {
        System.out.println(ANSI_BOLD + title + ANSI_RESET);
    }

    private void printRow(String label, String value) {
        System.out.printf("  %-20s %s%n", label, value);
    }

    private String formatSize(long bytes) {
        if (bytes >= 1L << 30) return String.format("%.1f GB", bytes / (double) (1L << 30));
        if (bytes >= 1L << 20) return String.format("%.1f MB", bytes / (double) (1L << 20));
        if (bytes >= 1L << 10) return String.format("%.1f KB", bytes / (double) (1L << 10));
        return bytes + " B";
    }
}