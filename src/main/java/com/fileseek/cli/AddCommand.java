package com.fileseek.cli;

import com.fileseek.cli.display.ProgressBar;
import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import com.fileseek.index.IndexManager;
import com.fileseek.scanner.DirectoryScanner;
import com.fileseek.scanner.ScanResult;
import com.fileseek.storage.IndexLock;
import com.fileseek.util.PathUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.*;
import java.util.concurrent.Callable;

@Command(
        name = "add",
        mixinStandardHelpOptions = true,
        description = "Add a directory to the FileSeek index."
)
public class AddCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Directory path to add.")
    private String path;

    @Override
    public Integer call() {
        Path dir;
        try {
            dir = PathUtils.expand(path);
        } catch (Exception e) {
            System.err.println("[error] Invalid path: " + path);
            return 2;
        }

        if (!Files.isDirectory(dir)) {
            System.err.printf(
                    "[error] '%s' is not a directory or does not exist.%n"
                            + "        Check the path and try again.%n", dir);
            return 2;
        }

        IndexLock lock = new IndexLock();
        if (!lock.acquire()) return 1;

        try {
            AppConfig config = ConfigManager.load();
            config.addWatchedDirectory(dir.toString());
            ConfigManager.save(config);
            System.out.println("Added: " + dir);

            DirectoryScanner scanner = new DirectoryScanner();
            System.out.print("Counting files... ");
            int total = scanner.countIndexableFiles(dir, config);
            System.out.printf("%,d files found%n", total);

            IndexManager indexManager = new IndexManager();
            indexManager.load();

            ProgressBar bar = new ProgressBar(total);
            long startMs = System.currentTimeMillis();

            ScanResult result = indexManager.indexDirectory(dir, config,
                    (count, file) ->
                            bar.update(count, Path.of(file).getFileName().toString()));

            bar.complete(
                    result.getFilesIndexed() + result.getFilesUpdated(),
                    System.currentTimeMillis() - startMs);

            System.out.printf(
                    "  %,d new  |  %,d updated  |  %,d removed  |  %d errors%n",
                    result.getFilesIndexed(), result.getFilesUpdated(),
                    result.getFilesRemoved(), result.getErrors());

            System.out.print("Saving index... ");
            long saveStart = System.currentTimeMillis();
            indexManager.save();
            System.out.printf("done (%.2fs)%n",
                    (System.currentTimeMillis() - saveStart) / 1000.0);

            System.out.printf("%nIndex: %,d documents  |  %,d unique terms%n",
                    indexManager.documentCount(), indexManager.termCount());

            return 0;

        } finally {
            lock.release();
        }
    }
}