package com.fileseek.index;

import com.fileseek.model.FileMetadata;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DocumentStore {

    private final Map<Integer, FileMetadata> store = new ConcurrentHashMap<>();
    private final Map<String, Integer> pathIndex = new ConcurrentHashMap<>();
    private final AtomicInteger nextDocId = new AtomicInteger(1);

    public int addDocument(FileMetadata metadata) {
        int docId = nextDocId.getAndIncrement();
        metadata.setDocId(docId);
        store.put(docId, metadata);
        pathIndex.put(metadata.getPath(), docId);
        return docId;
    }

    public Optional<FileMetadata> getDocument(int docId) {
        return Optional.ofNullable(store.get(docId));
    }

    public boolean removeDocument(int docId) {
        FileMetadata meta = store.remove(docId);
        if (meta != null) {
            pathIndex.remove(meta.getPath());
            return true;
        }
        return false;
    }

    public boolean containsPath(String path) {
        return pathIndex.containsKey(path);
    }

    public Optional<Integer> getDocIdByPath(String path) {
        return Optional.ofNullable(pathIndex.get(path));
    }

    public Optional<FileMetadata> getByPath(String path) {
        return getDocIdByPath(path).flatMap(this::getDocument);
    }

    public Collection<FileMetadata> getAllDocuments() {
        return Collections.unmodifiableCollection(store.values());
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
        pathIndex.clear();
        nextDocId.set(1);
    }

    public void restoreDocument(FileMetadata metadata) {
        store.put(metadata.getDocId(), metadata);
        pathIndex.put(metadata.getPath(), metadata.getDocId());
        if (metadata.getDocId() >= nextDocId.get()) {
            nextDocId.set(metadata.getDocId() + 1);
        }
    }
}