package com.fileseek.index;

import com.fileseek.model.FileMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStoreTest {

    private DocumentStore store;

    @BeforeEach
    void setUp() {
        store = new DocumentStore();
    }

    @Test
    void addDocumentAssignsDocId() {
        FileMetadata meta = meta("/notes/test.txt");
        int docId = store.addDocument(meta);
        assertTrue(docId > 0);
        assertEquals(docId, meta.getDocId());
    }

    @Test
    void addDocumentAssignsIncrementalIds() {
        int id1 = store.addDocument(meta("/a.txt"));
        int id2 = store.addDocument(meta("/b.txt"));
        assertEquals(id1 + 1, id2);
    }

    @Test
    void getDocumentReturnsCorrectMetadata() {
        FileMetadata meta = meta("/notes/test.txt");
        int docId = store.addDocument(meta);

        Optional<FileMetadata> result = store.getDocument(docId);
        assertTrue(result.isPresent());
        assertEquals("/notes/test.txt", result.get().getPath());
    }

    @Test
    void getDocumentReturnsEmptyForUnknownId() {
        Optional<FileMetadata> result = store.getDocument(999);
        assertTrue(result.isEmpty());
    }

    @Test
    void containsPathReturnsTrueAfterAdd() {
        store.addDocument(meta("/projects/app.java"));
        assertTrue(store.containsPath("/projects/app.java"));
    }

    @Test
    void containsPathReturnsFalseForUnknownPath() {
        assertFalse(store.containsPath("/nonexistent/file.txt"));
    }

    @Test
    void getDocIdByPathReturnsCorrectId() {
        int docId = store.addDocument(meta("/docs/readme.md"));
        Optional<Integer> result = store.getDocIdByPath("/docs/readme.md");
        assertTrue(result.isPresent());
        assertEquals(docId, result.get());
    }

    @Test
    void removeDocumentDeletesFromBothMaps() {
        int docId = store.addDocument(meta("/tmp/file.txt"));
        boolean removed = store.removeDocument(docId);

        assertTrue(removed);
        assertTrue(store.getDocument(docId).isEmpty());
        assertFalse(store.containsPath("/tmp/file.txt"));
    }

    @Test
    void removeDocumentReturnsFalseForUnknownId() {
        assertFalse(store.removeDocument(999));
    }

    @Test
    void sizeReflectsAddAndRemove() {
        assertEquals(0, store.size());
        int docId = store.addDocument(meta("/a.txt"));
        assertEquals(1, store.size());
        store.removeDocument(docId);
        assertEquals(0, store.size());
    }

    @Test
    void clearResetsEverything() {
        store.addDocument(meta("/a.txt"));
        store.addDocument(meta("/b.txt"));
        store.clear();
        assertEquals(0, store.size());
        assertFalse(store.containsPath("/a.txt"));
    }

    @Test
    void restoreDocumentRebuildsState() {
        FileMetadata meta = new FileMetadata(42, "/restored.txt",
                "restored.txt", ".txt", 100, 0L);
        store.restoreDocument(meta);

        assertTrue(store.containsPath("/restored.txt"));
        assertEquals(42, store.getDocument(42).get().getDocId());
        // Next assigned docId must be > 42
        int nextId = store.addDocument(meta("/new.txt"));
        assertTrue(nextId > 42);
    }

    @Test
    void getByPathReturnsMetadata() {
        store.addDocument(meta("/notes/file.txt"));
        Optional<FileMetadata> result = store.getByPath("/notes/file.txt");
        assertTrue(result.isPresent());
        assertEquals("/notes/file.txt", result.get().getPath());
    }

    @Test
    void averageDocumentLengthReturnsOneForEmptyStore() {
        assertEquals(1.0, store.averageDocumentLength(), 1e-10);
    }

    @Test
    void averageDocumentLengthComputesCorrectly() {
        FileMetadata m1 = meta("/a.txt");
        FileMetadata m2 = meta("/b.txt");
        store.addDocument(m1);
        store.addDocument(m2);
        m1.setTokenCount(100);
        m2.setTokenCount(200);

        // Average of 100 and 200 = 150
        assertEquals(150.0, store.averageDocumentLength(), 1e-10);
    }

    // --- helper ---

    private FileMetadata meta(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String ext = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.'))
                : "";
        return new FileMetadata(0, path, fileName, ext, 1024L,
                System.currentTimeMillis());
    }
}
