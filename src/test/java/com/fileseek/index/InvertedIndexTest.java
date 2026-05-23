package com.fileseek.index;

import com.fileseek.model.Posting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvertedIndexTest {

    private InvertedIndex index;

    @BeforeEach
    void setUp() {
        index = new InvertedIndex();
    }

    @Test
    void addPostingCreatesEntry() {
        index.addPosting("redis", 1, 3);
        assertTrue(index.containsTerm("redis"));
    }

    @Test
    void addPostingRecordsPosition() {
        index.addPosting("redis", 1, 3);
        List<Posting> postings = index.getPostings("redis");
        assertEquals(1, postings.size());
        assertEquals(1, postings.get(0).docId());
        assertTrue(postings.get(0).positions().contains(3));
    }

    @Test
    void multiplePositionsSameDocSamePosting() {
        index.addPosting("spring", 1, 0);
        index.addPosting("spring", 1, 7);
        index.addPosting("spring", 1, 15);

        List<Posting> postings = index.getPostings("spring");
        assertEquals(1, postings.size());
        assertEquals(3, postings.get(0).frequency());
        assertTrue(postings.get(0).positions().containsAll(List.of(0, 7, 15)));
    }

    @Test
    void multipleDocsSameTerm() {
        index.addPosting("docker", 1, 5);
        index.addPosting("docker", 2, 3);
        index.addPosting("docker", 3, 9);

        List<Posting> postings = index.getPostings("docker");
        assertEquals(3, postings.size());
    }

    @Test
    void documentFrequencyCountsDistinctDocs() {
        index.addPosting("redis", 1, 0);
        index.addPosting("redis", 1, 5); // same doc
        index.addPosting("redis", 2, 2);

        assertEquals(2, index.documentFrequency("redis"));
    }

    @Test
    void getPostingsReturnsEmptyForUnknownTerm() {
        List<Posting> postings = index.getPostings("nonexistent");
        assertTrue(postings.isEmpty());
    }

    @Test
    void containsTermReturnsFalseForUnknownTerm() {
        assertFalse(index.containsTerm("ghost"));
    }

    @Test
    void removeDocumentCleansPostings() {
        index.addPosting("redis", 1, 3);
        index.addPosting("redis", 2, 7);
        index.removeDocument(1);

        List<Posting> postings = index.getPostings("redis");
        assertEquals(1, postings.size());
        assertEquals(2, postings.get(0).docId());
    }

    @Test
    void removeDocumentRemovesTermWhenNoPostingsRemain() {
        index.addPosting("redis", 1, 3);
        index.removeDocument(1);

        assertFalse(index.containsTerm("redis"));
        assertEquals(0, index.termCount());
    }

    @Test
    void removeDocumentAcrossMultipleTerms() {
        index.addPosting("spring", 1, 0);
        index.addPosting("boot", 1, 1);
        index.addPosting("spring", 2, 0);

        index.removeDocument(1);

        assertFalse(index.containsTerm("boot"), "only doc1 had 'boot'");
        assertTrue(index.containsTerm("spring"), "doc2 still has 'spring'");
    }

    @Test
    void termCountReflectsDistinctTerms() {
        index.addPosting("redis", 1, 0);
        index.addPosting("docker", 1, 1);
        index.addPosting("redis", 2, 0);
        assertEquals(2, index.termCount());
    }

    @Test
    void clearRemovesEverything() {
        index.addPosting("redis", 1, 0);
        index.addPosting("spring", 2, 1);
        index.clear();
        assertEquals(0, index.termCount());
        assertFalse(index.containsTerm("redis"));
    }

    @Test
    void getAllTermsReturnsAllIndexedTerms() {
        index.addPosting("redis", 1, 0);
        index.addPosting("docker", 2, 0);
        index.addPosting("spring", 3, 0);
        assertTrue(index.getAllTerms().containsAll(
                List.of("redis", "docker", "spring")));
    }

    @Test
    void restorePostingsRebuildsState() {
        List<Posting> postings = List.of(new Posting(1, List.of(3, 18)));
        index.restorePostings("redis", postings);

        assertTrue(index.containsTerm("redis"));
        assertEquals(1, index.getPostings("redis").size());
    }

    @Test
    void concurrentAddPostingDoesNotCorruptData()
            throws InterruptedException {
        // 10 threads each adding postings for a unique docId
        Thread[] threads = new Thread[10];
        for (int t = 0; t < 10; t++) {
            final int docId = t + 1;
            threads[t] = new Thread(() -> {
                for (int pos = 0; pos < 100; pos++) {
                    index.addPosting("redis", docId, pos);
                }
            });
        }
        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        // Each doc should have exactly 100 positions for "redis"
        List<Posting> postings = index.getPostings("redis");
        assertEquals(10, postings.size(),
                "10 documents must have postings after concurrent writes");
        postings.forEach(p ->
                assertEquals(100, p.frequency(),
                        "Each doc must have exactly 100 positions"));
    }
}
