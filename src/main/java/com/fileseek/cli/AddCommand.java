package com.fileseek.cli;

import com.fileseek.cli.display.ProgressBar;
import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import com.fileseek.index.IndexManager;
import com.fileseek.scanner.DirectoryScanner;
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

        // Pass 1 — count so the progress bar has a total
        DirectoryScanner scanner = new DirectoryScanner();
        System.out.print("Counting files... ");
        int total = scanner.countIndexableFiles(dir, config);
        System.out.printf("%,d files found%n", total);

        // Load existing index
        IndexManager indexManager = new IndexManager();
        indexManager.load();

        // Pass 2 — index with progress bar
        ProgressBar bar = new ProgressBar(total);
        long startMs = System.currentTimeMillis();

        ScanResult result = indexManager.indexDirectory(dir, config,
                (count, file) -> bar.update(count, Path.of(file).getFileName().toString()));

        bar.complete(result.getFilesIndexed() + result.getFilesUpdated(),
                System.currentTimeMillis() - startMs);

        // Summary
        System.out.printf("  %,d new  |  %,d updated  |  %,d removed  |  %d errors%n",
                result.getFilesIndexed(),
                result.getFilesUpdated(),
                result.getFilesRemoved(),
                result.getErrors());

        // Save
        System.out.print("Saving index... ");
        long saveStart = System.currentTimeMillis();
        indexManager.save();
        System.out.printf("done (%.2fs)%n",
                (System.currentTimeMillis() - saveStart) / 1000.0);

        // Index stats
        System.out.printf("%nIndex: %,d documents  |  %,d unique terms%n",
                indexManager.documentCount(),
                indexManager.termCount());
    }
}