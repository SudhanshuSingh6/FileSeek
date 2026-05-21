package com.fileseek.cli;

import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import com.fileseek.index.IndexManager;
import com.fileseek.scanner.FileSystemWatcher;
import com.fileseek.storage.IndexLock;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Path;

@Command(
        name = "watch",
        mixinStandardHelpOptions = true,
        description = "Watch indexed directories for changes and update the index live."
)
public class WatchCommand implements Runnable {

    IndexLock lock = new IndexLock();

    @Override
    public void run() {
        if (!lock.acquire())
            return;
        if (!IndexManager.indexExists()) {
            System.out.println("No index found. Run 'fileseek add <directory>' first.");
            return;
        }

        AppConfig config = ConfigManager.load();

        if (config.getWatchedDirectories().isEmpty()) {
            System.out.println("No directories configured. " +
                    "Run 'fileseek add <directory>' first.");
            return;
        }

        IndexManager indexManager = new IndexManager();
        indexManager.load();

        System.out.printf("Loaded index — %d documents%n",
                indexManager.documentCount());

        FileSystemWatcher watcher;
        try {
            watcher = new FileSystemWatcher(indexManager, config,
                    System.out::println);
        } catch (IOException e) {
            System.err.println("[error] Could not create watcher: " + e.getMessage());
            return;
        }

        int registered = 0;
        for (String dir : config.getWatchedDirectories()) {
            try {
                watcher.register(Path.of(dir));
                System.out.println("Watching: " + dir);
                registered++;
            } catch (IOException e) {
                System.err.printf("[warn] Could not watch %s: %s%n",
                        dir, e.getMessage());
            }
        }

        if (registered == 0) {
            System.err.println("[error] No directories could be registered.");
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nSaving index...");
            watcher.stop();
            indexManager.save();
            lock.release();
            System.out.println("Index saved. Goodbye.");
        }));

        watcher.watch();
    }
}