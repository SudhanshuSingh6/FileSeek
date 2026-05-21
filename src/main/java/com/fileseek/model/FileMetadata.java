package com.fileseek.model;

import com.fileseek.util.PathUtils;

public class FileMetadata {

    private int docId;
    private String path;
    private String fileName;
    private String extension;
    private long sizeBytes;
    private long lastModified;
    private long indexedAt;
    private int tokenCount;

    public FileMetadata() {
    }

    public FileMetadata(int docId, String path, String fileName,
                        String extension, long sizeBytes, long lastModified) {
        this.docId = docId;
        this.path = path;
        this.fileName = fileName;
        this.extension = extension;
        this.sizeBytes = sizeBytes;
        this.lastModified = lastModified;
        this.indexedAt = System.currentTimeMillis();
    }

    public int getDocId() {
        return docId;
    }

    public String getPath() {
        return path;
    }

    public String getFileName() {
        return fileName;
    }

    public String getExtension() {
        return extension;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public long getLastModified() {
        return lastModified;
    }

    public long getIndexedAt() {
        return indexedAt;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setDocId(int v) {
        this.docId = v;
    }

    public void setIndexedAt(long v) {
        this.indexedAt = v;
    }

    public void setTokenCount(int v) {
        this.tokenCount = v;
    }

    public String getFolderPath() {
        return PathUtils.parentOf(path);
    }

    @Override
    public String toString() {
        return String.format(
                "FileMetadata{docId=%d, path='%s', size=%d}", docId, path, sizeBytes);
    }
}