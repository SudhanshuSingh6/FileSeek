package com.fileseek.cli;

import com.fileseek.config.ConfigManager;
import picocli.CommandLine.Command;

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
        // Phase 6 — index file deletion added here later
        System.out.println("Reset complete. Run 'fileseek' to set up again.");
    }
}