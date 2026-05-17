package com.fileseek.model;

public class FileMetadata {

    private int    docId;
    private String path;
    private String fileName;
    private String extension;
    private long   sizeBytes;
    private long   lastModified;   // epoch millis
    private long   indexedAt;      // epoch millis

    public FileMetadata() {}

    public FileMetadata(int docId, String path, String fileName,
                        String extension, long sizeBytes, long lastModified) {
        this.docId        = docId;
        this.path         = path;
        this.fileName     = fileName;
        this.extension    = extension;
        this.sizeBytes    = sizeBytes;
        this.lastModified = lastModified;
        this.indexedAt    = System.currentTimeMillis();
    }

    public int    getDocId()        { return docId; }
    public String getPath()         { return path; }
    public String getFileName()     { return fileName; }
    public String getExtension()    { return extension; }
    public long   getSizeBytes()    { return sizeBytes; }
    public long   getLastModified() { return lastModified; }
    public long   getIndexedAt()    { return indexedAt; }

    public void setDocId(int docId)             { this.docId = docId; }
    public void setIndexedAt(long indexedAt)    { this.indexedAt = indexedAt; }

    public String getFolderPath() {
        int sep = path.lastIndexOf('/');
        return (sep > 0) ? path.substring(0, sep) : path;
    }

    @Override
    public String toString() {
        return String.format("FileMetadata{docId=%d, path='%s', size=%d, lastModified=%d}",
                docId, path, sizeBytes, lastModified);
    }
}