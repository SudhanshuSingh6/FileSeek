package com.fileseek;

import com.fileseek.cli.FileSeekCommand;
import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import com.fileseek.config.FirstRunSetup;
import picocli.CommandLine;

import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class FileSeekApplication {

    public static void main(String[] args) {
        if (ConfigManager.isFirstRun() && !isBypassCommand(args)) {
            new FirstRunSetup().run();
        } else if (!isBypassCommand(args)) {
            validateConfig();
        }

        int exitCode = new CommandLine(new FileSeekCommand()).execute(args);
        System.exit(exitCode);
    }

    private static void validateConfig() {
        AppConfig config = ConfigManager.load();

        List<String> missing = config.getWatchedDirectories()
                .stream()
                .filter(dir -> !Files.isDirectory(Path.of(dir)))
                .collect(Collectors.toList());

        if (missing.isEmpty()) return;

        System.out.println("[warn] These configured directories no longer exist:");
        missing.forEach(dir -> System.out.println("  " + dir));
        System.out.println("  Run 'fileseek remove <path>' to clean up,");
        System.out.println("  or re-attach the drive/volume and try again.");
        System.out.println();
    }

    private static boolean isBypassCommand(String[] args) {
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")
                    || arg.equals("--version") || arg.equals("-V")) {
                return true;
            }
        }
        return false;
    }
}