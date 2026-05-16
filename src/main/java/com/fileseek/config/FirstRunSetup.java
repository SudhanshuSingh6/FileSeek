package com.fileseek.config;

import java.io.File;
import java.nio.file.*;
import java.util.*;

public class FirstRunSetup {

    private static final int MAX_FOLDERS_SHOWN = 8;

    public AppConfig run() {
        Scanner scanner = new Scanner(System.in);

        printWelcome();

        List<Path> topFolders = discoverTopFolders();
        printFolderMenu(topFolders);

        Path chosen = promptFolderSelection(scanner, topFolders);
        IndexingScope scope = promptIndexingScope(scanner);

        AppConfig config = new AppConfig();
        config.addWatchedDirectory(chosen.toString());

        if (scope == IndexingScope.QUICK) {
            // Quick scan: lower size thresholds, fewer directories
            config.setMaxTextFileSizeBytes(5L * 1024 * 1024);
            config.setMaxPdfFileSizeBytes(2L * 1024 * 1024);
        }

        ConfigManager.save(config);

        System.out.println();
        System.out.println("Configuration saved to ~/.fileseek/config.json");
        System.out.println("Run 'fileseek add <path>' to index more directories.");
        System.out.println();

        return config;
    }

    private void printWelcome() {
        System.out.println();
        System.out.println("Welcome to FileSeek");
        System.out.println("-------------------");
        System.out.println("Detected home directory: " + System.getProperty("user.home"));
        System.out.println();
    }

    private List<Path> discoverTopFolders() {
        Path home = Path.of(System.getProperty("user.home"));
        List<Path> folders = new ArrayList<>();

        try (var stream = Files.list(home)) {
            stream.filter(p -> Files.isDirectory(p))
                    .filter(p -> !isHiddenOrSystem(p))
                    .sorted()
                    .limit(MAX_FOLDERS_SHOWN)
                    .forEach(folders::add);
        } catch (Exception e) {
            System.err.println("[warn] Could not list home directory: " + e.getMessage());
        }

        return folders;
    }

    private boolean isHiddenOrSystem(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(".")
                || name.equalsIgnoreCase("Library")   // macOS
                || name.equalsIgnoreCase("AppData");   // Windows
    }

    private void printFolderMenu(List<Path> folders) {
        System.out.println("Top folders:");
        System.out.println();
        for (int i = 0; i < folders.size(); i++) {
            System.out.printf("  [%d] %s%n", i + 1, folders.get(i).getFileName());
        }
        System.out.printf("  [%d] Custom path%n", folders.size() + 1);
        System.out.println();
    }

    private Path promptFolderSelection(Scanner scanner, List<Path> folders) {
        while (true) {
            System.out.print("Select a folder to index [1-" + (folders.size() + 1) + "]: ");
            String input = scanner.nextLine().trim();

            try {
                int choice = Integer.parseInt(input);

                if (choice >= 1 && choice <= folders.size()) {
                    Path chosen = folders.get(choice - 1);
                    System.out.println("Selected: " + chosen);
                    return chosen;
                }

                if (choice == folders.size() + 1) {
                    return promptCustomPath(scanner);
                }

            } catch (NumberFormatException ignored) {}

            System.out.println("Invalid selection. Please enter a number between 1 and "
                    + (folders.size() + 1) + ".");
        }
    }

    private Path promptCustomPath(Scanner scanner) {
        while (true) {
            System.out.print("Enter custom path: ");
            String input = scanner.nextLine().trim();
            Path path = Path.of(input.replace("~", System.getProperty("user.home")));

            if (Files.isDirectory(path)) {
                return path;
            }
            System.out.println("Path does not exist or is not a directory: " + input);
        }
    }

    private IndexingScope promptIndexingScope(Scanner scanner) {
        System.out.println();
        System.out.println("Indexing scope:");
        System.out.println();
        System.out.println("  [1] Quick scan  (smaller files, faster startup)");
        System.out.println("  [2] Full scan   (all supported files)");
        System.out.println();

        while (true) {
            System.out.print("Select scope [1-2]: ");
            String input = scanner.nextLine().trim();
            switch (input) {
                case "1": return IndexingScope.QUICK;
                case "2": return IndexingScope.FULL;
                default:  System.out.println("Enter 1 or 2.");
            }
        }
    }

    private enum IndexingScope { QUICK, FULL }
}