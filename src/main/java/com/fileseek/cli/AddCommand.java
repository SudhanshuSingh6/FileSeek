package com.fileseek.cli;

import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import com.fileseek.index.IndexManager;
import com.fileseek.scanner.ScanResult;
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

        IndexManager indexManager = new IndexManager();
        indexManager.load();

        System.out.println("Indexing...");
        ScanResult result = indexManager.indexDirectory(
                dir, config,
                (count, file) -> System.out.printf("\r  Files processed: %d  ", count)
        );

        System.out.println();
        System.out.println(result);

        System.out.print("Saving index... ");
        indexManager.save();
        System.out.println("done.");
    }
}