package com.fileseek.scanner;

import com.fileseek.config.AppConfig;
import com.fileseek.index.IndexManager;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Background filesystem watcher using Java WatchService.
 * <p>
 * Watches all registered directories recursively.
 * On ENTRY_CREATE and ENTRY_MODIFY — re-indexes the file.
 * On ENTRY_DELETE — removes the file from the index.
 * On new directory creation — registers it automatically.
 * <p>
 * Index is saved after each event batch to avoid excessive I/O.
 * <p>
 * Note: on Linux/macOS, WatchService uses native inotify/kqueue events
 * (low latency). On Windows it may fall back to polling (higher latency).
 */
public class FileSystemWatcher {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final WatchService watchService;
    private final Map<WatchKey, Path> keyToDir = new HashMap<>();
    private final IndexManager indexManager;
    private final AppConfig config;
    private final Consumer<String> onEvent;    // UI callback
    private volatile boolean running = false;

    public FileSystemWatcher(IndexManager indexManager,
                             AppConfig config,
                             Consumer<String> onEvent) throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.indexManager = indexManager;
        this.config = config;
        this.onEvent = onEvent;
    }

    /**
     * Register a directory and all its subdirectories recursively.
     * Called once per watched root before starting the watch loop.
     */
    public void register(Path root) throws IOException {
        Files.walkFileTree(root, Set.of(), Integer.MAX_VALUE,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path dir, BasicFileAttributes attrs) throws IOException {
                        String name = dir.getFileName().toString();
                        if (config.isIgnored(name) || name.startsWith(".")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        registerDirectory(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    /**
     * Start the watch loop — blocks the calling thread.
     * Call this on a dedicated thread or via WatchCommand.
     */
    public void watch() {
        running = true;
        log("Watching for changes. Press Ctrl+C to stop.");

        while (running) {
            WatchKey key;
            try {
                // Block until an event arrives
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }

            Path dir = keyToDir.get(key);
            if (dir == null) {
                key.reset();
                continue;
            }

            boolean indexChanged = false;

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == OVERFLOW) continue;
                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path file = dir.resolve(pathEvent.context());

                boolean changed = handleEvent(kind, file);
                indexChanged = indexChanged || changed;
            }
            if (indexChanged) {
                indexManager.save();
            }

            boolean valid = key.reset();
            if (!valid) {
                keyToDir.remove(key);
                if (keyToDir.isEmpty()) break;
            }
        }

        log("Watcher stopped.");
    }

    public void stop() {
        running = false;
        try {
            watchService.close();
        } catch (IOException ignored) {
        }
    }

    private boolean handleEvent(WatchEvent.Kind<?> kind, Path file) {
        if (kind == ENTRY_CREATE && Files.isDirectory(file)) {
            try {
                registerDirectory(file);
                log("Directory added: " + file);
            } catch (IOException e) {
                log("[warn] Could not watch new directory: " + file);
            }
            return false;
        }

        if (kind == ENTRY_CREATE) {
            if (indexManager.reindexFile(file, config)) {
                log("Indexed:  " + file.getFileName());
                return true;
            }
            return false;
        }

        if (kind == ENTRY_MODIFY) {
            if (indexManager.reindexFile(file, config)) {
                log("Updated:  " + file.getFileName());
                return true;
            }
            return false;
        }

        if (kind == ENTRY_DELETE) {
            String path = file.toAbsolutePath().toString();
            if (indexManager.removeDocument(path)) {
                log("Removed:  " + file.getFileName());
                return true;
            }
            return false;
        }

        return false;
    }

    private void registerDirectory(Path dir) throws IOException {
        WatchKey key = dir.register(watchService,
                ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        keyToDir.put(key, dir);
    }

    private void log(String message) {
        String time = LocalTime.now().format(TIME_FMT);
        String line = String.format("[%s] %s", time, message);
        if (onEvent != null) onEvent.accept(line);
        else System.out.println(line);
    }
}