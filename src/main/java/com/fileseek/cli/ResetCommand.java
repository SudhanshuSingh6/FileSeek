package com.fileseek.cli;

import com.fileseek.config.ConfigManager;
import com.fileseek.index.IndexManager;
import com.fileseek.util.SearchHistory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Scanner;
import java.util.concurrent.Callable;

@Command(
        name = "reset",
        mixinStandardHelpOptions = true,
        description = "Delete all configuration, index data, and search history."
)
public class ResetCommand implements Callable<Integer> {

    @Option(names = {"-y", "--yes"},
            description = "Skip confirmation prompt.")
    private boolean yes;

    @Override
    public Integer call() {
        if (!yes) {
            System.out.print(
                    "This will delete all config, index, and history. Continue? [y/N]: ");
            String input = new Scanner(System.in).nextLine().trim();
            if (!input.equalsIgnoreCase("y")) {
                System.out.println("Reset cancelled.");
                return 1;
            }
        }

        ConfigManager.delete();
        SearchHistory.clear();

        try {
            Files.deleteIfExists(IndexManager.getIndexFile());
        } catch (IOException e) {
            System.err.printf(
                    "[error] Could not delete index file: %s%n"
                            + "        Delete manually: %s%n",
                    e.getMessage(), IndexManager.getIndexFile());
        }

        System.out.println("Reset complete.");
        System.out.println("Run 'fileseek' to set up again.");
        return 0;
    }
}