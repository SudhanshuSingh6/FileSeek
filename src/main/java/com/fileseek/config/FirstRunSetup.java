package com.fileseek.config;

import com.fileseek.util.PathUtils;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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
            config.setMaxTextFileSizeBytes(5L * 1024 * 1024);
            config.setMaxPdfFileSizeBytes(2L * 1024 * 1024);
        }

        ConfigManager.save(config);

        System.out.println();
        System.out.println("Configuration saved to "
                + ConfigManager.getConfigFilePath());
        System.out.println("Run 'fileseek add <path>' to index more directories.");
        System.out.println();

        return config;
    }

    private void printWelcome() {
        System.out.println();
        System.out.println("Welcome to FileSeek");
        System.out.println("-------------------");
        System.out.println("Detected home directory: "
                + System.getProperty("user.home"));
        System.out.println();
    }

    private List<Path> discoverTopFolders() {
        Path home = Path.of(System.getProperty("user.home"));
        List<Path> folders = new ArrayList<>();
        try (var stream = Files.list(home)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> !isHiddenOrSystem(p))
                    .sorted()
                    .limit(MAX_FOLDERS_SHOWN)
                    .forEach(folders::add);
        } catch (IOException e) {
            System.err.println("[warn] Could not list home directory: "
                    + e.getMessage());
        }
        return folders;
    }

    private boolean isHiddenOrSystem(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(".")
                || name.equalsIgnoreCase("Library")
                || name.equalsIgnoreCase("AppData");
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
            System.out.print("Select a folder [1-" + (folders.size() + 1) + "]: ");
            String input = scanner.nextLine().trim();
            try {
                int choice = Integer.parseInt(input);
                if (choice >= 1 && choice <= folders.size()) {
                    return folders.get(choice - 1);
                }
                if (choice == folders.size() + 1) {
                    return promptCustomPath(scanner);
                }
            } catch (NumberFormatException ignored) {
            }
            System.out.println("Enter a number between 1 and "
                    + (folders.size() + 1) + ".");
        }
    }

    private Path promptCustomPath(Scanner scanner) {
        while (true) {
            System.out.print("Enter custom path: ");
            String input = scanner.nextLine().trim();
            try {
                Path path = PathUtils.expand(input);
                if (Files.isDirectory(path)) return path;
            } catch (Exception ignored) {
            }
            System.out.println("Not a valid directory: " + input);
        }
    }

    private IndexingScope promptIndexingScope(Scanner scanner) {
        System.out.println();
        System.out.println("Indexing scope:");
        System.out.println("  [1] Quick scan  (smaller files, faster startup)");
        System.out.println("  [2] Full scan   (all supported files)");
        System.out.println();
        while (true) {
            System.out.print("Select scope [1-2]: ");
            switch (scanner.nextLine().trim()) {
                case "1":
                    return IndexingScope.QUICK;
                case "2":
                    return IndexingScope.FULL;
                default:
                    System.out.println("Enter 1 or 2.");
            }
        }
    }

    private enum IndexingScope {QUICK, FULL}
}