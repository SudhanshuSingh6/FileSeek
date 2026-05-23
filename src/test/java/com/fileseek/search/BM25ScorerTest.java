package com.fileseek.search;

import com.fileseek.index.DocumentStore;
import com.fileseek.model.FileMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BM25ScorerTest {

    private DocumentStore store;
    private BM25Scorer scorer;

    @BeforeEach
    void setUp() {
        store = new DocumentStore();
        scorer = new BM25Scorer(store);
    }

    private int addDoc(String path, int tokenCount) {
        FileMetadata meta = new FileMetadata(
                0, path, "file.txt", ".txt", 1024L, 0L);
        int id = store.addDocument(meta);
        meta.setTokenCount(tokenCount);
        return id;
    }

    @Test
    void zeroFrequencyScoresZero() {
        int id = addDoc("/a.txt", 100);
        assertEquals(0.0, scorer.score(0, id, 10, 3));
    }

    @Test
    void zeroDocFrequencyScoresZero() {
        int id = addDoc("/a.txt", 100);
        assertEquals(0.0, scorer.score(5, id, 10, 0));
    }

    @Test
    void zeroTotalDocsScoresZero() {
        int id = addDoc("/a.txt", 100);
        assertEquals(0.0, scorer.score(5, id, 0, 3));
    }

    @Test
    void positiveScoreForValidInputs() {
        int id = addDoc("/a.txt", 100);
        assertTrue(scorer.score(3, id, 100, 10) > 0);
    }

    @Test
    void tfSaturates() {
        // BM25 key property: 10× frequency must NOT produce 10× score
        int id1 = addDoc("/a.txt", 200);
        int id2 = addDoc("/b.txt", 200);

        double score1 = scorer.score(1, id1, 100, 10);
        double score10 = scorer.score(10, id2, 100, 10);

        assertTrue(score10 > score1, "Higher frequency must score higher");
        assertTrue(score10 < score1 * 10,
                "BM25 TF must saturate — not grow linearly with frequency");
    }

    @Test
    void rareTermScoresHigherThanCommonTerm() {
        int id = addDoc("/a.txt", 100);
        double rareScore = scorer.score(3, id, 100, 1);  // 1/100 docs
        double commonScore = scorer.score(3, id, 100, 80);  // 80/100 docs
        assertTrue(rareScore > commonScore);
    }

    @Test
    void longerDocScoresLowerForSameFrequency() {
        // BM25 length normalization
        int shortDocId = addDoc("/short.txt", 50);
        int longDocId = addDoc("/long.txt", 500);

        double shortScore = scorer.score(3, shortDocId, 100, 10);
        double longScore = scorer.score(3, longDocId, 100, 10);

        assertTrue(shortScore > longScore,
                "Short documents must score higher for the same term frequency");
    }

    @Test
    void scoreIsNonNegativeWhenTermInEveryDoc() {
        int id = addDoc("/a.txt", 100);
        double score = scorer.score(3, id, 100, 100);
        assertTrue(score >= 0,
                "IDF component is still positive due to +1 smoothing");
    }

    @Test
    void higherFrequencyGivesHigherScore() {
        int id1 = addDoc("/a.txt", 100);
        int id2 = addDoc("/b.txt", 100);

        double low = scorer.score(1, id1, 100, 10);
        double high = scorer.score(5, id2, 100, 10);

        assertTrue(high > low);
    }

    @Test
    void fallbackWhenTokenCountIsZero() {
        // Metadata-only docs have tokenCount = 0
        FileMetadata meta = new FileMetadata(
                0, "/meta.pdf", "meta.pdf", ".pdf", 10_000_000L, 0L);
        int id = store.addDocument(meta);
        // tokenCount left at 0

        double score = scorer.score(1, id, 100, 5);
        assertFalse(Double.isNaN(score), "Score must not be NaN");
        assertFalse(Double.isInfinite(score), "Score must not be infinite");
        assertTrue(score >= 0, "Score must be non-negative");
    }

    @Test
    void cacheInvalidationDoesNotBreakScoring() {
        int id = addDoc("/a.txt", 100);
        double before = scorer.score(3, id, 100, 10);
        scorer.invalidateCache();
        double after = scorer.score(3, id, 100, 10);
        assertEquals(before, after, 1e-10,
                "Score must be identical before and after cache invalidation");
    }

    @Test
    void scoreConsistentAcrossMultipleCallsSameInputs() {
        int id = addDoc("/a.txt", 100);
        double s1 = scorer.score(3, id, 100, 10);
        double s2 = scorer.score(3, id, 100, 10);
        assertEquals(s1, s2, 1e-10, "Scorer must be deterministic");
    }
}
