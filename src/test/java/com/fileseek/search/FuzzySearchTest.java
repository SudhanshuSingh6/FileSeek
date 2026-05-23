package com.fileseek.search;

import com.fileseek.index.IndexManager;
import com.fileseek.model.FileMetadata;
import com.fileseek.model.QueryOptions;
import com.fileseek.model.SearchResult;
import com.fileseek.util.Tokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.fileseek.search.FuzzySearch.levenshtein;
import static org.junit.jupiter.api.Assertions.*;

class FuzzySearchTest {

    private IndexManager indexManager;
    private SearchEngine engine;

    @BeforeEach
    void setUp() {
        indexManager = new IndexManager();
        engine = new SearchEngine(indexManager);
    }

    private void index(String path, String fileName, String ext, String content) {
        FileMetadata meta = new FileMetadata(
                0, path, fileName, ext, 1024L, System.currentTimeMillis());
        indexManager.indexDocument(meta, Tokenizer.tokenize(content));
    }

    // -------------------------------------------------------
    // Levenshtein unit tests
    // -------------------------------------------------------

    @Test
    void levenshteinExactMatchIsZero() {
        assertEquals(0, levenshtein("redis", "redis", 2));
    }

    @Test
    void levenshteinSingleSubstitution() {
        assertEquals(1, levenshtein("spring", "speing", 2));
    }

    @Test
    void levenshteinSingleInsertion() {
        assertEquals(1, levenshtein("redis", "reedis", 2));
    }

    @Test
    void levenshteinSingleDeletion() {
        assertEquals(1, levenshtein("spring", "sprig", 2));
    }

    @Test
    void levenshteinTwoEdits() {
        assertEquals(2, levenshtein("docker", "docxxr", 2));
    }

    @Test
    void levenshteinReturnsMaxPlusOneWhenExceeded() {
        int result = levenshtein("abc", "xyz", 2);
        assertTrue(result > 2,
                "Completely different strings must exceed maxDistance");
    }

    @Test
    void levenshteinEarlyExitOnLengthGap() {
        // Length difference alone exceeds maxDistance — must short-circuit
        int result = levenshtein("hi", "hello world", 2);
        assertTrue(result > 2);
    }

    @Test
    void levenshteinBothEmpty() {
        assertEquals(0, levenshtein("", "", 2));
    }

    @Test
    void levenshteinOneEmpty() {
        // "abc" vs "" = 3 deletions; maxDistance=5 allows it
        assertEquals(3, levenshtein("abc", "", 5));
        assertEquals(3, levenshtein("", "abc", 5));
    }

    @Test
    void levenshteinTypicalTypo() {
        // "sprng" is a common typo for "spring" (missing 'i')
        assertEquals(1, levenshtein("sprng", "spring", 2));
    }

    @Test
    void levenshteinSymmetric() {
        assertEquals(
                levenshtein("redis", "reedis", 2),
                levenshtein("reedis", "redis", 2));
    }

    // -------------------------------------------------------
    // fuzzy search integration tests
    // -------------------------------------------------------

    @Test
    void fuzzySearchFindsTypo() {
        index("/notes/spring.txt", "spring.txt", ".txt",
                "spring boot simplifies development");

        QueryOptions opts = QueryOptions.builder("sprng").fuzzy(true).build();
        List<SearchResult> results = engine.search(opts);

        assertFalse(results.isEmpty(),
                "Fuzzy search must find 'spring' when queried with 'sprng'");
    }

    @Test
    void fuzzySearchFindsOneEditDistance() {
        index("/notes/docker.txt", "docker.txt", ".txt",
                "docker containers improve deployment");

        QueryOptions opts = QueryOptions.builder("dockr").fuzzy(true).build();
        assertFalse(engine.search(opts).isEmpty());
    }

    @Test
    void fuzzySearchReturnsEmptyForLargeEditDistance() {
        index("/notes/file.txt", "file.txt", ".txt", "redis caching");

        QueryOptions opts = QueryOptions.builder("xyz").fuzzy(true).build();
        assertTrue(engine.search(opts).isEmpty());
    }

    @Test
    void fuzzySearchRanksExactMatchAboveFuzzyMatch() {
        // doc1 has exact term "redis"
        // doc2 has "rediis" (typo) — distance 1
        index("/exact.txt", "exact.txt", ".txt", "redis caching layer");
        index("/approx.txt", "approx.txt", ".txt", "rediis configuration");

        QueryOptions opts = QueryOptions.builder("redis").fuzzy(true).build();
        List<SearchResult> results = engine.search(opts);

        assertFalse(results.isEmpty());
        assertEquals("/exact.txt", results.get(0).getMetadata().getPath(),
                "Exact match (distance 0, multiplier 1.0) must rank above "
                        + "fuzzy match (distance 1, multiplier 0.75)");
    }

    @Test
    void fuzzySearchMultipleQueryTerms() {
        index("/notes/file.txt", "file.txt", ".txt",
                "spring boot redis caching");

        // Both terms have typos
        QueryOptions opts = QueryOptions.builder("sprng rediss")
                .fuzzy(true).build();
        assertFalse(engine.search(opts).isEmpty());
    }

    @Test
    void fuzzySearchIsCaseInsensitive() {
        // Tokenizer lowercases at index time; fuzzy search on lowercase tokens
        index("/notes/file.txt", "file.txt", ".txt", "Spring Boot");

        QueryOptions opts = QueryOptions.builder("sprng").fuzzy(true).build();
        assertFalse(engine.search(opts).isEmpty());
    }

    @Test
    void fuzzySearchReturnsPositiveScores() {
        index("/notes/file.txt", "file.txt", ".txt", "redis caching");

        QueryOptions opts = QueryOptions.builder("rediss").fuzzy(true).build();
        List<SearchResult> results = engine.search(opts);

        assertFalse(results.isEmpty());
        results.forEach(r ->
                assertTrue(r.getScore() > 0, "Fuzzy match must have positive score"));
    }

    @Test
    void fuzzyFlagDoesNotActivatePhraseLogic() {
        index("/notes/file.txt", "file.txt", ".txt", "spring boot redis");

        // --fuzzy with a plain query must use fuzzy logic, not phrase logic
        QueryOptions opts = QueryOptions.builder("sprng boot")
                .fuzzy(true).build();
        List<SearchResult> results = engine.search(opts);

        assertFalse(results.isEmpty(),
                "Fuzzy mode must not apply phrase positional constraints");
    }
}
