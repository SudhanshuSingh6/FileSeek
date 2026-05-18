package com.fileseek.cli;

import com.fileseek.config.ConfigManager;
import com.fileseek.index.IndexManager;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Scanner;

@Command(
        name = "reset",
        mixinStandardHelpOptions = true,
        description = "Reset all FileSeek configuration and indexes."
)
public class ResetCommand implements Runnable {

    @Override
    public void run() {
        System.out.print("This will delete all configuration and indexes. Continue? (y/n): ");
        String input = new Scanner(System.in).nextLine().trim();

        if (!input.equalsIgnoreCase("y")) {
            System.out.println("Reset cancelled.");
            return;
        }

        ConfigManager.delete();

        try {
            Files.deleteIfExists(IndexManager.getIndexFile());
        } catch (IOException e) {
            System.err.println("[warn] Could not delete index file: " + e.getMessage());
        }

        System.out.println("Reset complete. Run 'fileseek' to set up again.");
    }
}