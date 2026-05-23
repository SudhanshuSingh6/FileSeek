package com.fileseek.index;

import com.fileseek.model.FileMetadata;
import com.fileseek.model.Posting;
import com.fileseek.util.Tokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IndexManagerTest {

    private IndexManager manager;

    @BeforeEach
    void setUp() {
        manager = new IndexManager();
    }

    // --- indexDocument ---

    @Test
    void indexDocumentPopulatesDocumentStore() {
        manager.indexDocument(meta("/notes/redis.txt"), tokens("redis caching performance"));
        assertTrue(manager.isIndexed("/notes/redis.txt"));
        assertEquals(1, manager.documentCount());
    }

    @Test
    void indexDocumentPopulatesInvertedIndex() {
        manager.indexDocument(meta("/notes/redis.txt"), tokens("redis caching performance"));
        assertFalse(manager.getInvertedIndex().getPostings("redis").isEmpty());
    }

    @Test
    void indexDocumentSetsTokenCount() {
        FileMetadata m = meta("/notes/test.txt");
        List<String> toks = tokens("redis spring boot");
        manager.indexDocument(m, toks);

        int stored = manager.getDocumentStore()
                .getByPath("/notes/test.txt")
                .map(FileMetadata::getTokenCount)
                .orElse(-1);

        assertEquals(toks.size(), stored);
    }

    @Test
    void indexDocumentAssignsPositions() {
        manager.indexDocument(meta("/notes/test.txt"),
                List.of("redis", "spring", "redis", "docker"));

        List<Posting> postings = manager.getInvertedIndex().getPostings("redis");
        assertEquals(1, postings.size());
        assertTrue(postings.get(0).positions().containsAll(List.of(0, 2)),
                "redis must be recorded at positions 0 and 2");
    }

    @Test
    void multipleDocumentsIndexedCorrectly() {
        manager.indexDocument(meta("/a.txt"), tokens("redis caching"));
        manager.indexDocument(meta("/b.txt"), tokens("spring boot redis"));
        manager.indexDocument(meta("/c.txt"), tokens("docker containers"));

        assertEquals(3, manager.documentCount());

        List<Posting> redisPostings = manager.getInvertedIndex().getPostings("redis");
        assertEquals(2, redisPostings.size(),
                "'redis' must appear in exactly 2 documents");
    }

    // --- removeDocument ---

    @Test
    void removeDocumentDeletesFromBothStores() {
        manager.indexDocument(meta("/notes/redis.txt"), tokens("redis caching"));
        assertTrue(manager.isIndexed("/notes/redis.txt"));

        manager.removeDocument("/notes/redis.txt");

        assertFalse(manager.isIndexed("/notes/redis.txt"));
        assertTrue(manager.getInvertedIndex().getPostings("redis").isEmpty());
    }

    @Test
    void removeDocumentReturnsFalseForUnknownPath() {
        assertFalse(manager.removeDocument("/does/not/exist.txt"));
    }

    @Test
    void removeDocumentLeavesOtherDocumentsIntact() {
        manager.indexDocument(meta("/a.txt"), tokens("redis caching"));
        manager.indexDocument(meta("/b.txt"), tokens("redis spring"));

        manager.removeDocument("/a.txt");

        assertEquals(1, manager.documentCount());
        assertTrue(manager.isIndexed("/b.txt"));

        List<Posting> postings = manager.getInvertedIndex().getPostings("redis");
        assertEquals(1, postings.size(),
                "'redis' posting for /b.txt must remain after /a.txt removed");
    }

    // --- isIndexed ---

    @Test
    void isIndexedReturnsTrueAfterIndexing() {
        manager.indexDocument(meta("/test.txt"), tokens("redis"));
        assertTrue(manager.isIndexed("/test.txt"));
    }

    @Test
    void isIndexedReturnsFalseForUnknownPath() {
        assertFalse(manager.isIndexed("/not/indexed.txt"));
    }

    // --- counts ---

    @Test
    void documentCountReflectsAddAndRemove() {
        assertEquals(0, manager.documentCount());
        manager.indexDocument(meta("/a.txt"), tokens("redis"));
        assertEquals(1, manager.documentCount());
        manager.removeDocument("/a.txt");
        assertEquals(0, manager.documentCount());
    }

    @Test
    void termCountReflectsIndexedTerms() {
        manager.indexDocument(meta("/a.txt"), tokens("redis spring docker"));
        assertEquals(3, manager.termCount());
    }

    // --- clear ---

    @Test
    void clearResetsAllState() {
        manager.indexDocument(meta("/a.txt"), tokens("redis caching"));
        manager.indexDocument(meta("/b.txt"), tokens("spring boot"));
        manager.clear();

        assertEquals(0, manager.documentCount());
        assertEquals(0, manager.termCount());
        assertFalse(manager.isIndexed("/a.txt"));
    }

    // --- concurrent indexing ---

    @Test
    void concurrentIndexingProducesCorrectResults()
            throws InterruptedException {

        Thread[] threads = new Thread[10];
        for (int t = 0; t < 10; t++) {
            int start = t * 10;
            threads[t] = new Thread(() -> {
                for (int i = start; i < start + 10; i++) {
                    manager.indexDocument(
                            meta("/file" + i + ".txt"),
                            tokens("redis spring docker " + i));
                }
            });
        }

        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        assertEquals(100, manager.documentCount(),
                "All 100 documents must be indexed without race conditions");

        long uniqueIds = manager.getDocumentStore()
                .getAllDocuments().stream()
                .mapToInt(FileMetadata::getDocId)
                .distinct()
                .count();
        assertEquals(100, uniqueIds,
                "Every document must have a unique docId");

        List<Posting> redisPostings = manager.getInvertedIndex()
                .getPostings("redis");
        assertEquals(100, redisPostings.size(),
                "'redis' must appear in all 100 documents");
    }

    // --- helpers ---

    private FileMetadata meta(String path) {
        String name = Path.of(path).getFileName().toString();
        String ext = name.contains(".")
                ? name.substring(name.lastIndexOf('.')) : "";
        return new FileMetadata(0, path, name, ext, 1024L,
                System.currentTimeMillis());
    }

    private List<String> tokens(String text) {
        return Tokenizer.tokenize(text);
    }
}
