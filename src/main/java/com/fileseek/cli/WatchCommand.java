package com.fileseek.cli;

import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import com.fileseek.index.IndexManager;
import com.fileseek.scanner.FileSystemWatcher;
import com.fileseek.storage.IndexLock;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "watch",
        mixinStandardHelpOptions = true,
        description = "Watch indexed directories for changes and update the index live."
)
public class WatchCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        if (!IndexManager.indexExists()) {
            System.err.println(
                    "[error] No index found.\n"
                            + "        Run 'fileseek add <directory>' first.");
            return 2;
        }

        AppConfig config = ConfigManager.load();

        if (config.getWatchedDirectories().isEmpty()) {
            System.err.println(
                    "[error] No directories configured.\n"
                            + "        Run 'fileseek add <directory>' first.");
            return 2;
        }

        IndexLock lock = new IndexLock();
        if (!lock.acquire()) return 1;

        IndexManager indexManager = new IndexManager();
        indexManager.load();

        System.out.printf("Loaded index — %,d documents%n",
                indexManager.documentCount());

        FileSystemWatcher watcher;
        try {
            watcher = new FileSystemWatcher(indexManager, config,
                    msg -> System.out.println(msg));
        } catch (IOException e) {
            lock.release();
            System.err.printf(
                    "[error] Could not create filesystem watcher: %s%n"
                            + "        Check that your OS supports Java WatchService.%n",
                    e.getMessage());
            return 1;
        }

        int registered = 0;
        for (String dir : config.getWatchedDirectories()) {
            try {
                watcher.register(Path.of(dir));
                System.out.println("Watching: " + dir);
                registered++;
            } catch (IOException e) {
                System.err.printf(
                        "[warn] Could not watch '%s': %s%n"
                                + "       Directory may be missing or inaccessible.%n",
                        dir, e.getMessage());
            }
        }

        if (registered == 0) {
            lock.release();
            System.err.println(
                    "[error] No directories could be registered for watching.\n"
                            + "        Run 'fileseek config' to check your configured paths.");
            return 1;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nSaving index...");
            watcher.stop();
            indexManager.save();
            lock.release();
            System.out.println("Index saved. Goodbye.");
        }));

        if (FileSeekCommand.verbose) {
            System.out.printf("  [verbose] Registered %d director%s%n",
                    registered, registered == 1 ? "y" : "ies");
        }

        watcher.watch();
        return 0;
    }
}