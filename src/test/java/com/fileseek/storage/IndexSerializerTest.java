package com.fileseek.storage;

import com.fileseek.index.DocumentStore;
import com.fileseek.index.InvertedIndex;
import com.fileseek.model.FileMetadata;
import com.fileseek.model.Posting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class IndexSerializerTest {

    @TempDir
    Path tempDir;

    // --- roundtrip ---

    @Test
    void serializeDeserializeRoundtrip() throws IOException {
        DocumentStore docStore = new DocumentStore();
        InvertedIndex index = new InvertedIndex();

        FileMetadata meta1 = new FileMetadata(0,
                "/notes/backend.txt", "backend.txt", ".txt", 1024L, 1_000_000L);
        FileMetadata meta2 = new FileMetadata(0,
                "/projects/chat.md", "chat.md", ".md", 2048L, 2_000_000L);

        int doc1 = docStore.addDocument(meta1);
        int doc2 = docStore.addDocument(meta2);

        index.addPosting("redis", doc1, 3);
        index.addPosting("redis", doc1, 18);
        index.addPosting("redis", doc2, 7);
        index.addPosting("spring", doc1, 0);

        Path indexFile = tempDir.resolve("fileseek.idx");
        new IndexSerializer(indexFile).serialize(docStore, index);

        DocumentStore restoredDocs = new DocumentStore();
        InvertedIndex restoredIndex = new InvertedIndex();
        new IndexDeserializer(indexFile).deserialize(restoredDocs, restoredIndex);

        // Document store assertions
        assertEquals(2, restoredDocs.size());
        assertTrue(restoredDocs.containsPath("/notes/backend.txt"));
        assertTrue(restoredDocs.containsPath("/projects/chat.md"));

        FileMetadata restored1 = restoredDocs.getDocument(doc1).orElseThrow();
        assertEquals("/notes/backend.txt", restored1.getPath());
        assertEquals("backend.txt", restored1.getFileName());
        assertEquals(".txt", restored1.getExtension());
        assertEquals(1024L, restored1.getSizeBytes());
        assertEquals(1_000_000L, restored1.getLastModified());

        // Inverted index assertions
        assertEquals(2, restoredIndex.termCount());

        List<Posting> redisPostings = restoredIndex.getPostings("redis");
        assertEquals(2, redisPostings.size());

        List<Posting> springPostings = restoredIndex.getPostings("spring");
        assertEquals(1, springPostings.size());
        assertEquals(doc1, springPostings.get(0).docId());
    }

    @Test
    void deltaEncodingPreservesPositions() throws IOException {
        DocumentStore docStore = new DocumentStore();
        InvertedIndex index = new InvertedIndex();

        FileMetadata meta = new FileMetadata(0,
                "/test.txt", "test.txt", ".txt", 512L, 0L);
        int docId = docStore.addDocument(meta);

        index.addPosting("java", docId, 3);
        index.addPosting("java", docId, 18);
        index.addPosting("java", docId, 45);

        Path indexFile = tempDir.resolve("delta_test.idx");
        new IndexSerializer(indexFile).serialize(docStore, index);

        DocumentStore restoredDocs = new DocumentStore();
        InvertedIndex restoredIndex = new InvertedIndex();
        new IndexDeserializer(indexFile).deserialize(restoredDocs, restoredIndex);

        List<Integer> positions = restoredIndex
                .getPostings("java").get(0).positions();

        // Absolute positions must be restored correctly from deltas
        assertEquals(List.of(3, 18, 45), positions);
    }

    @Test
    void singlePositionRoundtrip() throws IOException {
        DocumentStore docStore = new DocumentStore();
        InvertedIndex index = new InvertedIndex();

        FileMetadata meta = new FileMetadata(0,
                "/single.txt", "single.txt", ".txt", 100L, 0L);
        int docId = docStore.addDocument(meta);
        index.addPosting("only", docId, 0);

        Path indexFile = tempDir.resolve("single.idx");
        new IndexSerializer(indexFile).serialize(docStore, index);

        DocumentStore restoredDocs = new DocumentStore();
        InvertedIndex restoredIndex = new InvertedIndex();
        new IndexDeserializer(indexFile).deserialize(restoredDocs, restoredIndex);

        assertEquals(List.of(0),
                restoredIndex.getPostings("only").get(0).positions());
    }

    @Test
    void emptyIndexRoundtrip() throws IOException {
        DocumentStore docStore = new DocumentStore();
        InvertedIndex index = new InvertedIndex();

        Path indexFile = tempDir.resolve("empty.idx");
        new IndexSerializer(indexFile).serialize(docStore, index);

        DocumentStore restoredDocs = new DocumentStore();
        InvertedIndex restoredIndex = new InvertedIndex();
        new IndexDeserializer(indexFile).deserialize(restoredDocs, restoredIndex);

        assertEquals(0, restoredDocs.size());
        assertEquals(0, restoredIndex.termCount());
    }

    @Test
    void nextDocIdRestoredCorrectly() throws IOException {
        DocumentStore docStore = new DocumentStore();
        InvertedIndex index = new InvertedIndex();

        docStore.addDocument(new FileMetadata(
                0, "/a.txt", "a.txt", ".txt", 100L, 0L));
        docStore.addDocument(new FileMetadata(
                0, "/b.txt", "b.txt", ".txt", 100L, 0L));

        Path indexFile = tempDir.resolve("docid.idx");
        new IndexSerializer(indexFile).serialize(docStore, index);

        DocumentStore restoredDocs = new DocumentStore();
        InvertedIndex restoredIndex = new InvertedIndex();
        new IndexDeserializer(indexFile).deserialize(restoredDocs, restoredIndex);

        FileMetadata newDoc = new FileMetadata(
                0, "/c.txt", "c.txt", ".txt", 100L, 0L);
        int newId = restoredDocs.addDocument(newDoc);
        assertTrue(newId > 2, "nextDocId must be ahead of all restored IDs");
    }

    // --- tokenCount roundtrip (version 2) ---

    @Test
    void tokenCountRoundtrip() throws IOException {
        DocumentStore docStore = new DocumentStore();
        InvertedIndex index = new InvertedIndex();

        FileMetadata meta = new FileMetadata(
                0, "/notes/test.txt", "test.txt", ".txt", 1024L, 0L);
        int docId = docStore.addDocument(meta);
        meta.setTokenCount(247);
        index.addPosting("redis", docId, 0);

        Path indexFile = tempDir.resolve("tokencount.idx");
        new IndexSerializer(indexFile).serialize(docStore, index);

        DocumentStore restoredDocs = new DocumentStore();
        InvertedIndex restoredIndex = new InvertedIndex();
        new IndexDeserializer(indexFile).deserialize(restoredDocs, restoredIndex);

        int restored = restoredDocs.getDocument(docId)
                .map(FileMetadata::getTokenCount)
                .orElse(-1);

        assertEquals(247, restored,
                "tokenCount must survive serialization roundtrip for BM25 scoring");
    }

    @Test
    void zeroTokenCountRoundtrip() throws IOException {
        DocumentStore docStore = new DocumentStore();
        InvertedIndex index = new InvertedIndex();

        FileMetadata meta = new FileMetadata(
                0, "/meta.pdf", "meta.pdf", ".pdf", 10_000_000L, 0L);
        int docId = docStore.addDocument(meta);
        // tokenCount left at 0 — metadata-only document

        Path indexFile = tempDir.resolve("zero_tokens.idx");
        new IndexSerializer(indexFile).serialize(docStore, index);

        DocumentStore restoredDocs = new DocumentStore();
        InvertedIndex restoredIndex = new InvertedIndex();
        new IndexDeserializer(indexFile).deserialize(restoredDocs, restoredIndex);

        int restored = restoredDocs.getDocument(docId)
                .map(FileMetadata::getTokenCount)
                .orElse(-1);

        assertEquals(0, restored, "Zero tokenCount must round-trip correctly");
    }

    // --- corruption detection ---

    @Test
    void corruptionCheckerDetectsMissingFile() {
        Path missing = tempDir.resolve("missing.idx");
        assertFalse(CorruptionChecker.isCorrupted(missing));
    }

    @Test
    void corruptionCheckerDetectsGarbageFile() throws IOException {
        Path garbage = tempDir.resolve("garbage.idx");
        java.nio.file.Files.writeString(garbage, "this is not a valid index file");
        assertTrue(CorruptionChecker.isCorrupted(garbage));
    }

    @Test
    void corruptionCheckerPassesValidFile() throws IOException {
        DocumentStore docStore = new DocumentStore();
        InvertedIndex index = new InvertedIndex();
        docStore.addDocument(new FileMetadata(
                0, "/valid.txt", "valid.txt", ".txt", 100L, 0L));

        Path indexFile = tempDir.resolve("valid.idx");
        new IndexSerializer(indexFile).serialize(docStore, index);

        assertFalse(CorruptionChecker.isCorrupted(indexFile));
    }

    @Test
    void deserializerThrowsOnBadMagic() throws IOException {
        Path badMagic = tempDir.resolve("badmagic.idx");
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(
                        new GZIPOutputStream(
                                new FileOutputStream(badMagic.toFile()))))) {
            out.writeInt(0xDEADBEEF);              // wrong magic
            out.writeInt(IndexSerializer.VERSION);  // correct version — irrelevant
        }

        DocumentStore restoredDocs = new DocumentStore();
        InvertedIndex restoredIndex = new InvertedIndex();

        assertThrows(IOException.class, () ->
                new IndexDeserializer(badMagic)
                        .deserialize(restoredDocs, restoredIndex));
    }

    @Test
    void versionMismatchThrowsDescriptiveMessage() throws IOException {
        Path oldVersion = tempDir.resolve("oldversion.idx");
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(
                        new GZIPOutputStream(
                                new FileOutputStream(oldVersion.toFile()))))) {
            out.writeInt(IndexSerializer.MAGIC);
            out.writeInt(0);   // version 0 — outdated
        }

        IOException ex = assertThrows(IOException.class, () ->
                new IndexDeserializer(oldVersion)
                        .deserialize(new DocumentStore(), new InvertedIndex()));

        assertTrue(ex.getMessage().toLowerCase().contains("version"),
                "Version mismatch message must mention 'version' "
                        + "so IndexManager can show the correct rebuild prompt");
    }

    @Test
    void longPathRoundtrip() throws IOException {
        DocumentStore docStore = new DocumentStore();
        InvertedIndex index = new InvertedIndex();

        String longPath = "/home/user/" + "a".repeat(500) + "/file.txt";
        FileMetadata meta = new FileMetadata(
                0, longPath, "file.txt", ".txt", 100L, 0L);
        docStore.addDocument(meta);

        Path indexFile = tempDir.resolve("longpath.idx");
        new IndexSerializer(indexFile).serialize(docStore, index);

        DocumentStore restoredDocs = new DocumentStore();
        InvertedIndex restoredIndex = new InvertedIndex();
        new IndexDeserializer(indexFile).deserialize(restoredDocs, restoredIndex);

        assertTrue(restoredDocs.containsPath(longPath),
                "Paths > 65535 chars must survive custom string encoding");
    }
}
