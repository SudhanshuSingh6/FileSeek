package com.fileseek.search;

import com.fileseek.index.IndexManager;
import com.fileseek.model.FileMetadata;
import com.fileseek.model.QueryOptions;
import com.fileseek.model.SearchResult;
import com.fileseek.util.Tokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchEngineTest {

    private IndexManager indexManager;
    private SearchEngine engine;

    @BeforeEach
    void setUp() {
        indexManager = new IndexManager();
        engine = new SearchEngine(indexManager);
    }

    // --- helpers ---

    private void index(String path, String fileName, String ext, String content) {
        FileMetadata meta = new FileMetadata(
                0, path, fileName, ext, 1024L, System.currentTimeMillis());
        indexManager.indexDocument(meta, Tokenizer.tokenize(content));
    }

    private List<SearchResult> search(String query) {
        return engine.search(QueryOptions.builder(query).build());
    }

    // -------------------------------------------------------
    // basic keyword search
    // -------------------------------------------------------

    @Test
    void findsDocumentByKeyword() {
        index("/notes/backend.txt", "backend.txt", ".txt",
                "redis caching improves backend performance");

        List<SearchResult> results = search("redis");

        assertFalse(results.isEmpty());
        assertEquals("/notes/backend.txt",
                results.get(0).getMetadata().getPath());
    }

    @Test
    void returnsEmptyForUnknownTerm() {
        index("/notes/file.txt", "file.txt", ".txt", "spring boot application");
        assertTrue(search("docker").isEmpty());
    }

    @Test
    void multiKeywordMatchesDocContainingAllTerms() {
        index("/a.txt", "a.txt", ".txt", "redis caching docker containers");
        index("/b.txt", "b.txt", ".txt", "spring boot framework");

        List<SearchResult> results = search("redis docker");

        assertEquals(1, results.size());
        assertEquals("/a.txt", results.get(0).getMetadata().getPath());
    }

    @Test
    void multiKeywordRanksHigherMatchFirst() {
        // doc1 has both terms; doc2 has only one
        index("/doc1.txt", "doc1.txt", ".txt",
                "redis caching redis performance redis");
        index("/doc2.txt", "doc2.txt", ".txt", "redis database");

        List<SearchResult> results = search("redis caching");

        assertFalse(results.isEmpty());
        assertEquals("/doc1.txt", results.get(0).getMetadata().getPath(),
                "doc matching more terms must rank first");
    }

    @Test
    void returnsEmptyForBlankQuery() {
        index("/file.txt", "file.txt", ".txt", "some content");
        assertTrue(search("   ").isEmpty());
    }

    @Test
    void returnsEmptyForAllStopWords() {
        index("/file.txt", "file.txt", ".txt", "some content");
        assertTrue(search("the and or").isEmpty());
    }

    // -------------------------------------------------------
    // ranking
    // -------------------------------------------------------

    @Test
    void resultsAreSortedByScoreDescending() {
        index("/high.txt", "high.txt", ".txt",
                "redis redis redis redis redis");
        index("/low.txt", "low.txt", ".txt",
                "redis spring boot docker");

        List<SearchResult> results = search("redis");

        assertEquals(2, results.size());
        assertTrue(results.get(0).getScore() >= results.get(1).getScore());
    }

    @Test
    void bm25ScoresSaturateForHighFrequencyTerms() {
        // BM25: 10× frequency must not produce 10× score
        index("/low.txt", "low.txt", ".txt", "redis");
        index("/high.txt", "high.txt", ".txt",
                "redis redis redis redis redis redis redis redis redis redis");

        List<SearchResult> results = search("redis");

        assertEquals(2, results.size());
        double highScore = results.get(0).getScore();
        double lowScore = results.get(1).getScore();

        assertTrue(highScore > lowScore,
                "Higher frequency must still rank above lower frequency");
        assertTrue(highScore < lowScore * 10,
                "BM25 saturation: 10× frequency must not give 10× score");
    }

    @Test
    void filenameMatchBoostsScore() {
        // doc1: "redis" in content only, no filename match
        FileMetadata meta1 = new FileMetadata(
                0, "/notes/backend.txt", "backend.txt", ".txt",
                1024L, System.currentTimeMillis());
        indexManager.indexDocument(meta1,
                Tokenizer.tokenize("redis caching performance"));

        // doc2: "redis" in filename — scanner merges content + filename tokens
        FileMetadata meta2 = new FileMetadata(
                0, "/notes/redis-guide.txt", "redis-guide.txt", ".txt",
                1024L, System.currentTimeMillis());
        List<String> tokens2 = new ArrayList<>(
                Tokenizer.tokenize("performance optimization tips"));
        tokens2.addAll(Tokenizer.tokenizeFilename("redis-guide.txt")); // adds "redis"
        indexManager.indexDocument(meta2, tokens2);

        List<SearchResult> results = search("redis");

        assertFalse(results.isEmpty());
        assertEquals("/notes/redis-guide.txt",
                results.get(0).getMetadata().getPath(),
                "Filename match must rank above content-only match");
    }

    // -------------------------------------------------------
    // phrase search
    // -------------------------------------------------------

    @Test
    void phraseSearchFindsExactPhrase() {
        IndexManager mgr = new IndexManager();
        SearchEngine eng = new SearchEngine(mgr);

        FileMetadata meta = new FileMetadata(
                0, "/notes/spring.txt", "spring.txt", ".txt", 100L, 0L);
        mgr.indexDocument(meta,
                Tokenizer.tokenizePhrase("spring boot simplifies development"));

        QueryOptions opts = QueryOptions.builder("\"spring boot\"")
                .phrase(true).build();

        assertFalse(eng.search(opts).isEmpty());
    }

    @Test
    void phraseSearchDoesNotMatchNonConsecutiveTerms() {
        // spring=0, framework=1, helps=2, boot=3 — not consecutive
        IndexManager mgr = new IndexManager();
        SearchEngine eng = new SearchEngine(mgr);

        FileMetadata meta = new FileMetadata(
                0, "/test.txt", "test.txt", ".txt", 100L, 0L);
        mgr.indexDocument(meta,
                Tokenizer.tokenizePhrase("spring framework helps boot applications"));

        QueryOptions opts = QueryOptions.builder("\"spring boot\"")
                .phrase(true).build();

        assertTrue(eng.search(opts).isEmpty(),
                "spring(0) and boot(3) are not consecutive — must not match");
    }

    @Test
    void phraseSearchMatchesConsecutiveTerms() {
        IndexManager mgr = new IndexManager();
        SearchEngine eng = new SearchEngine(mgr);

        FileMetadata meta = new FileMetadata(
                0, "/test.txt", "test.txt", ".txt", 100L, 0L);
        // spring=0, boot=1 — consecutive
        mgr.indexDocument(meta,
                Tokenizer.tokenizePhrase("spring boot framework"));

        QueryOptions opts = QueryOptions.builder("\"spring boot\"")
                .phrase(true).build();

        assertFalse(eng.search(opts).isEmpty());
    }

    // -------------------------------------------------------
    // metadata filters
    // -------------------------------------------------------

    @Test
    void filterByExtension() {
        index("/notes/app.java", "app.java", ".java", "redis caching");
        index("/notes/app.md", "app.md", ".md", "redis caching");

        QueryOptions opts = QueryOptions.builder("redis")
                .filterExt(".java").build();
        List<SearchResult> results = engine.search(opts);

        assertEquals(1, results.size());
        assertEquals(".java", results.get(0).getMetadata().getExtension());
    }

    @Test
    void filterByMinSize() {
        FileMetadata small = new FileMetadata(
                0, "/small.txt", "small.txt", ".txt", 512L, 0L);
        FileMetadata large = new FileMetadata(
                0, "/large.txt", "large.txt", ".txt", 5_000_000L, 0L);

        indexManager.indexDocument(small, Tokenizer.tokenize("redis caching"));
        indexManager.indexDocument(large, Tokenizer.tokenize("redis caching"));

        QueryOptions opts = QueryOptions.builder("redis")
                .minSizeBytes(1_000_000L).build();
        List<SearchResult> results = engine.search(opts);

        assertEquals(1, results.size());
        assertEquals("/large.txt", results.get(0).getMetadata().getPath());
    }

    @Test
    void filterByModifiedAfter() {
        long sevenDaysAgo = System.currentTimeMillis() - 7L * 86_400_000L;
        long yesterday = System.currentTimeMillis() - 86_400_000L;
        long monthAgo = System.currentTimeMillis() - 30L * 86_400_000L;

        FileMetadata recent = new FileMetadata(
                0, "/recent.txt", "recent.txt", ".txt", 1024L, yesterday);
        FileMetadata old = new FileMetadata(
                0, "/old.txt", "old.txt", ".txt", 1024L, monthAgo);

        indexManager.indexDocument(recent, Tokenizer.tokenize("redis caching"));
        indexManager.indexDocument(old, Tokenizer.tokenize("redis caching"));

        QueryOptions opts = QueryOptions.builder("redis")
                .modifiedAfterEpoch(sevenDaysAgo).build();
        List<SearchResult> results = engine.search(opts);

        assertEquals(1, results.size());
        assertEquals("/recent.txt", results.get(0).getMetadata().getPath());
    }

    @Test
    void noFiltersReturnsAllMatches() {
        index("/a.java", "a.java", ".java", "redis");
        index("/b.md", "b.md", ".md", "redis");
        index("/c.txt", "c.txt", ".txt", "redis");

        List<SearchResult> results = search("redis");
        assertEquals(3, results.size());
    }

    // -------------------------------------------------------
    // result metadata
    // -------------------------------------------------------

    @Test
    void resultsHavePositiveScores() {
        index("/notes/file.txt", "file.txt", ".txt", "redis spring docker");
        List<SearchResult> results = search("redis");
        results.forEach(r ->
                assertTrue(r.getScore() > 0, "Every result must have a positive score"));
    }

    @Test
    void resultsHaveSearchDuration() {
        index("/notes/file.txt", "file.txt", ".txt", "redis caching");
        List<SearchResult> results = search("redis");
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getSearchDurationMs() >= 0);
    }
}
