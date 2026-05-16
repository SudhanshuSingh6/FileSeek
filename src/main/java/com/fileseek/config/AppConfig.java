package com.fileseek.config;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {

    private static final List<String> DEFAULT_IGNORED = List.of(
            ".git", "node_modules", "target", "build", "dist", ".idea"
    );

    private static final List<String> DEFAULT_EXTENSIONS = List.of(
            ".txt", ".md", ".java", ".json", ".xml", ".yml", ".properties", ".pdf"
    );

    private List<String> watchedDirectories  = new ArrayList<>();
    private List<String> ignoredDirectories  = new ArrayList<>(DEFAULT_IGNORED);
    private List<String> supportedExtensions = new ArrayList<>(DEFAULT_EXTENSIONS);
    private long maxTextFileSizeBytes        = 15L * 1024 * 1024; // 15 MB
    private long maxPdfFileSizeBytes         =  5L * 1024 * 1024; //  5 MB

    // --- watched directories ---

    public void addWatchedDirectory(String path) {
        if (!watchedDirectories.contains(path)) {
            watchedDirectories.add(path);
        }
    }

    public boolean removeWatchedDirectory(String path) {
        return watchedDirectories.remove(path);
    }

    public List<String> getWatchedDirectories()  { return watchedDirectories; }
    public List<String> getIgnoredDirectories()  { return ignoredDirectories; }
    public List<String> getSupportedExtensions() { return supportedExtensions; }
    public long getMaxTextFileSizeBytes()        { return maxTextFileSizeBytes; }
    public long getMaxPdfFileSizeBytes()         { return maxPdfFileSizeBytes; }

    public void setMaxTextFileSizeBytes(long v)  { maxTextFileSizeBytes = v; }
    public void setMaxPdfFileSizeBytes(long v)   { maxPdfFileSizeBytes  = v; }

    public boolean isIgnored(String folderName) {
        return ignoredDirectories.contains(folderName);
    }

    public boolean isSupportedExtension(String ext) {
        return supportedExtensions.contains(ext.toLowerCase());
    }
}