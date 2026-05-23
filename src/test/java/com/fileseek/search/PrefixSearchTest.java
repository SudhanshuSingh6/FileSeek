package com.fileseek.search;

import com.fileseek.index.DocumentStore;
import com.fileseek.index.IndexManager;
import com.fileseek.index.InvertedIndex;
import com.fileseek.model.FileMetadata;
import com.fileseek.model.QueryOptions;
import com.fileseek.model.SearchResult;
import com.fileseek.util.Tokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrefixSearchTest {

    private IndexManager indexManager;
    private SearchEngine engine;

    @BeforeEach
    void setUp() {
        indexManager = new IndexManager();
        engine = new SearchEngine(indexManager);
    }

    /**
     * Builds a PrefixSearch instance for unit tests that need direct access.
     * DocumentStore is shared so BM25Scorer can look up document lengths.
     */
    private PrefixSearch buildPrefixSearch(InvertedIndex idx) {
        DocumentStore ds = new DocumentStore();
        return new PrefixSearch(idx, ds, new BM25Scorer(ds));
    }

    private void index(String path, String fileName, String ext, String content) {
        FileMetadata meta = new FileMetadata(
                0, path, fileName, ext, 1024L, System.currentTimeMillis());
        indexManager.indexDocument(meta, Tokenizer.tokenize(content));
    }

    // -------------------------------------------------------
    // PrefixSearch.findMatchingTerms() — unit tests
    // -------------------------------------------------------

    @Test
    void findMatchingTermsReturnsCorrectTerms() {
        InvertedIndex idx = new InvertedIndex();
        idx.addPosting("docker", 1, 0);
        idx.addPosting("dockerfile", 1, 1);
        idx.addPosting("spring", 1, 2);
        idx.addPosting("dockyard", 1, 3);

        PrefixSearch ps = buildPrefixSearch(idx);
        List<String> matches = ps.findMatchingTerms("dock");

        assertEquals(3, matches.size());
        assertTrue(matches.contains("docker"));
        assertTrue(matches.contains("dockerfile"));
        assertTrue(matches.contains("dockyard"));
        assertFalse(matches.contains("spring"));
    }

    @Test
    void findMatchingTermsExactPrefixMatchesItself() {
        InvertedIndex idx = new InvertedIndex();
        idx.addPosting("docker", 1, 0);

        PrefixSearch ps = buildPrefixSearch(idx);
        assertTrue(ps.findMatchingTerms("docker").contains("docker"));
    }

    @Test
    void findMatchingTermsReturnsEmptyForNoMatch() {
        InvertedIndex idx = new InvertedIndex();
        idx.addPosting("spring", 1, 0);

        PrefixSearch ps = buildPrefixSearch(idx);
        assertTrue(ps.findMatchingTerms("redis").isEmpty());
    }

    @Test
    void findMatchingTermsHandlesEmptyPrefix() {
        InvertedIndex idx = new InvertedIndex();
        idx.addPosting("spring", 1, 0);

        PrefixSearch ps = buildPrefixSearch(idx);
        assertTrue(ps.findMatchingTerms("").isEmpty());
    }

    @Test
    void findMatchingTermsHandlesNullPrefix() {
        InvertedIndex idx = new InvertedIndex();
        idx.addPosting("spring", 1, 0);

        PrefixSearch ps = buildPrefixSearch(idx);
        assertTrue(ps.findMatchingTerms(null).isEmpty());
    }

    @Test
    void findMatchingTermsIsCaseSensitiveOnLowercaseIndex() {
        // Tokenizer lowercases everything — index contains only lowercase terms
        InvertedIndex idx = new InvertedIndex();
        idx.addPosting("docker", 1, 0);

        PrefixSearch ps = buildPrefixSearch(idx);
        // Prefix must also be lowercase to match
        assertTrue(ps.findMatchingTerms("dock").contains("docker"));
        assertTrue(ps.findMatchingTerms("DOCK").isEmpty(),
                "Uppercase prefix must not match lowercase index terms");
    }

    // -------------------------------------------------------
    // integration tests via SearchEngine
    // -------------------------------------------------------

    @Test
    void prefixSearchFindsDocumentByPrefix() {
        index("/notes/containers.txt", "containers.txt", ".txt",
                "docker containers improve deployment speed");

        QueryOptions opts = QueryOptions.builder("dock").prefix(true).build();
        List<SearchResult> results = engine.search(opts);

        assertFalse(results.isEmpty());
        assertEquals("/notes/containers.txt",
                results.get(0).getMetadata().getPath());
    }

    @Test
    void prefixSearchMatchesMultipleTermsInSameDoc() {
        index("/notes/spring.txt", "spring.txt", ".txt",
                "springframework springboot springcloud applications");

        QueryOptions opts = QueryOptions.builder("spring").prefix(true).build();
        List<SearchResult> results = engine.search(opts);

        // All three spring* tokens matched — document should score high
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getScore() > 0);
    }

    @Test
    void prefixSearchRanksLongerCoverageHigher() {
        // "docker" searched with prefix "docker" → coverage 1.0
        // "dockyard" searched with prefix "docker" → coverage 6/8 = 0.75
        index("/exact.txt", "exact.txt", ".txt", "docker containers");
        index("/partial.txt", "partial.txt", ".txt", "dockyard storage");

        QueryOptions opts = QueryOptions.builder("docker").prefix(true).build();
        List<SearchResult> results = engine.search(opts);

        assertFalse(results.isEmpty());
        assertEquals("/exact.txt", results.get(0).getMetadata().getPath(),
                "Exact prefix match (coverage 1.0) must rank above partial match");
    }

    @Test
    void prefixSearchReturnsEmptyForUnmatchedPrefix() {
        index("/notes/file.txt", "file.txt", ".txt", "spring boot framework");

        QueryOptions opts = QueryOptions.builder("xyz").prefix(true).build();
        assertTrue(engine.search(opts).isEmpty());
    }

    @Test
    void prefixSearchHandlesMultipleQueryTokens() {
        index("/notes/file.txt", "file.txt", ".txt",
                "docker redis caching containers deployment");

        QueryOptions opts = QueryOptions.builder("dock red").prefix(true).build();
        assertFalse(engine.search(opts).isEmpty());
    }

    @Test
    void prefixSearchWorksAfterTokenizerLowercases() {
        // Content "Docker" is indexed as "docker" by tokenizer
        index("/notes/file.txt", "file.txt", ".txt", "Docker containers");

        QueryOptions opts = QueryOptions.builder("dock").prefix(true).build();
        assertFalse(engine.search(opts).isEmpty());
    }

    @Test
    void prefixSearchReturnsPositiveScores() {
        index("/notes/file.txt", "file.txt", ".txt", "docker containers");

        QueryOptions opts = QueryOptions.builder("dock").prefix(true).build();
        List<SearchResult> results = engine.search(opts);

        assertFalse(results.isEmpty());
        results.forEach(r ->
                assertTrue(r.getScore() > 0, "Prefix match must have positive score"));
    }

    // -------------------------------------------------------
    // mode isolation
    // -------------------------------------------------------

    @Test
    void prefixFlagDoesNotActivateFuzzyLogic() {
        index("/notes/file.txt", "file.txt", ".txt", "docker containers");

        // --prefix should find "docker" via prefix scan, not fuzzy distance
        QueryOptions prefixOpts = QueryOptions.builder("dock").prefix(true).build();
        List<SearchResult> prefixResults = engine.search(prefixOpts);

        assertFalse(prefixResults.isEmpty(),
                "Prefix search must find 'docker' with prefix 'dock'");
    }

    @Test
    void prefixFlagDoesNotActivatePhraseLogic() {
        index("/notes/file.txt", "file.txt", ".txt", "spring boot framework");

        // --prefix with two tokens must NOT apply phrase positional constraints
        QueryOptions opts = QueryOptions.builder("spri boo").prefix(true).build();
        List<SearchResult> results = engine.search(opts);

        assertFalse(results.isEmpty(),
                "Prefix search must not apply phrase positional constraints");
    }
}
