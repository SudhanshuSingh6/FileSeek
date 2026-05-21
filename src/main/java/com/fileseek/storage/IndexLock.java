package com.fileseek.storage;

import com.fileseek.config.ConfigManager;

import java.io.IOException;
import java.nio.file.*;

public class IndexLock {

    private static final Path LOCK_FILE =
            ConfigManager.getIndexDirPath().resolve("fileseek.lock");

    private boolean held = false;

    public boolean acquire() {
        try {
            Files.createDirectories(LOCK_FILE.getParent());
            Files.writeString(
                    LOCK_FILE,
                    String.valueOf(ProcessHandle.current().pid()),
                    StandardOpenOption.CREATE_NEW);
            held = true;
            return true;

        } catch (FileAlreadyExistsException e) {
            return handleExistingLock();

        } catch (IOException e) {
            System.err.println("[warn] Could not create lock file: " + e.getMessage());
            return true;
        }
    }

    public void release() {
        if (!held) return;
        try {
            Files.deleteIfExists(LOCK_FILE);
            held = false;
        } catch (IOException e) {
            System.err.println("[warn] Could not release lock: " + e.getMessage());
        }
    }

    public boolean isHeld() {
        return held;
    }


    private boolean handleExistingLock() {
        try {
            String content = Files.readString(LOCK_FILE).trim();
            long pid = Long.parseLong(content);

            if (ProcessHandle.of(pid).isPresent()) {
                System.err.printf(
                        "[error] Another FileSeek process (PID %d) is using the index.%n"
                                + "        Wait for it to finish or kill it, then try again.%n",
                        pid);
                return false;
            }

            System.err.println(
                    "[warn] Stale lock file found (process no longer running). Removing.");
            Files.delete(LOCK_FILE);
            return acquire();

        } catch (IOException | NumberFormatException e) {
            try {
                Files.deleteIfExists(LOCK_FILE);
                return acquire();
            } catch (IOException ex) {
                return true;
            }
        }
    }
}