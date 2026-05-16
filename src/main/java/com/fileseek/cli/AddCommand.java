package com.fileseek.cli;

import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.*;

@Command(
        name = "add",
        mixinStandardHelpOptions = true,
        description = "Add a directory to the FileSeek index."
)
public class AddCommand implements Runnable {

    @Parameters(index = "0", description = "Directory path to add.")
    private String path;

    @Override
    public void run() {
        String resolved = path.replace("~", System.getProperty("user.home"));
        Path dir = Path.of(resolved);

        if (!Files.isDirectory(dir)) {
            System.err.println("Error: not a directory: " + resolved);
            return;
        }

        AppConfig config = ConfigManager.load();
        config.addWatchedDirectory(dir.toAbsolutePath().toString());
        ConfigManager.save(config);

        System.out.println("Added: " + dir.toAbsolutePath());
    }
}