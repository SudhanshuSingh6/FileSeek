package com.fileseek.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScanResult {

    private int filesIndexed = 0;
    private int filesUpdated = 0;
    private int filesRemoved = 0;
    private int filesSkipped = 0;
    private int metadataOnly = 0;
    private int directoriesScanned = 0;
    private int errors = 0;
    private long durationMs = 0;
    private final List<String> errorMessages = new ArrayList<>();

    public void setFilesRemoved(int count) {
        filesRemoved = count;
    }

    public void incrementIndexed() {
        filesIndexed++;
    }

    public void incrementUpdated() {
        filesUpdated++;
    }

    public void incrementRemoved() {
        filesRemoved++;
    }

    public void incrementSkipped() {
        filesSkipped++;
    }

    public void incrementMetadata() {
        metadataOnly++;
    }

    public void incrementDirectories() {
        directoriesScanned++;
    }

    public void incrementErrors() {
        errors++;
    }

    public void addError(String msg) {
        errorMessages.add(msg);
        errors++;
    }

    public void setDurationMs(long ms) {
        durationMs = ms;
    }

    public int getFilesIndexed() {
        return filesIndexed;
    }

    public int getFilesUpdated() {
        return filesUpdated;
    }

    public int getFilesRemoved() {
        return filesRemoved;
    }

    public int getFilesSkipped() {
        return filesSkipped;
    }

    public int getMetadataOnly() {
        return metadataOnly;
    }

    public int getDirectoriesScanned() {
        return directoriesScanned;
    }

    public int getErrors() {
        return errors;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public List<String> getErrorMessages() {
        return Collections.unmodifiableList(errorMessages);
    }

    public int totalProcessed() {
        return filesIndexed + filesUpdated + filesSkipped + metadataOnly;
    }

    @Override
    public String toString() {
        return String.format(
                "Indexed %d  Updated %d  Removed %d  Skipped %d  Errors %d  (%.2fs)",
                filesIndexed, filesUpdated, filesRemoved,
                filesSkipped, errors, durationMs / 1000.0);
    }
}