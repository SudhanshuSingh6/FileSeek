package com.fileseek.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ScanResult {

    private final AtomicInteger filesIndexed = new AtomicInteger();
    private final AtomicInteger filesUpdated = new AtomicInteger();
    private final AtomicInteger filesRemoved = new AtomicInteger();
    private final AtomicInteger filesSkipped = new AtomicInteger();
    private final AtomicInteger metadataOnly = new AtomicInteger();
    private final AtomicInteger directoriesScanned = new AtomicInteger();
    private final AtomicInteger errors = new AtomicInteger();
    private final List<String> errorMessages =
            Collections.synchronizedList(new ArrayList<>());
    private volatile long durationMs = 0;

    public void incrementIndexed() {
        filesIndexed.incrementAndGet();
    }

    public void incrementUpdated() {
        filesUpdated.incrementAndGet();
    }

    public void incrementRemoved() {
        filesRemoved.incrementAndGet();
    }

    public void incrementSkipped() {
        filesSkipped.incrementAndGet();
    }

    public void incrementMetadata() {
        metadataOnly.incrementAndGet();
    }

    public void incrementDirectories() {
        directoriesScanned.incrementAndGet();
    }

    public void incrementErrors() {
        errors.incrementAndGet();
    }

    public void addError(String msg) {
        errorMessages.add(msg);
        errors.incrementAndGet();
    }

    public void setDurationMs(long ms) {
        durationMs = ms;
    }

    public void setFilesRemoved(int n) {
        filesRemoved.set(n);
    }

    public int getFilesIndexed() {
        return filesIndexed.get();
    }

    public int getFilesUpdated() {
        return filesUpdated.get();
    }

    public int getFilesRemoved() {
        return filesRemoved.get();
    }

    public int getFilesSkipped() {
        return filesSkipped.get();
    }

    public int getMetadataOnly() {
        return metadataOnly.get();
    }

    public int getDirectoriesScanned() {
        return directoriesScanned.get();
    }

    public int getErrors() {
        return errors.get();
    }

    public long getDurationMs() {
        return durationMs;
    }

    public List<String> getErrorMessages() {
        return Collections.unmodifiableList(errorMessages);
    }

    public int totalProcessed() {
        return filesIndexed.get() + filesSkipped.get() + metadataOnly.get();
    }

    @Override
    public String toString() {
        return String.format(
                "Indexed %,d  Updated %,d  Removed %,d  Skipped %,d  Errors %d  (%.2fs)",
                filesIndexed.get(), filesUpdated.get(), filesRemoved.get(),
                filesSkipped.get(), errors.get(), durationMs / 1000.0);
    }
}