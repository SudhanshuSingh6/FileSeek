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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegexSearchTest {

    private IndexManager indexManager;
    private InvertedIndex invertedIndex;
    private DocumentStore documentStore;
    private RegexSearch regexSearch;
    private SearchEngine engine;

    @BeforeEach
    void setUp() {
        indexManager = new IndexManager();
        invertedIndex = indexManager.getInvertedIndex();
        documentStore = indexManager.getDocumentStore();
        regexSearch = new RegexSearch(invertedIndex, documentStore,
                new BM25Scorer(documentStore));
        engine = new SearchEngine(indexManager);
    }

    private void index(String path, String content) {
        FileMetadata meta = new FileMetadata(
                0, path, path, ".txt", 1024L, System.currentTimeMillis());
        indexManager.indexDocument(meta, Tokenizer.tokenize(content));
    }

    // -------------------------------------------------------
    // RegexSearch.search() — unit tests
    // -------------------------------------------------------

    @Test
    void exactPatternMatchesToken() {
        index("/a.txt", "redis caching performance");
        Map<Integer, Double> scores = regexSearch.search("redis");
        assertEquals(1, scores.size());
    }

    @Test
    void dotStarMatchesAnyToken() {
        index("/a.txt", "springframework springboot");
        Map<Integer, Double> scores = regexSearch.search("spring.*");
        assertEquals(1, scores.size());
    }

    @Test
    void dotStarMatchesMultipleTermsAccumulatesScore() {
        // All three tokens match — document scored for each
        index("/a.txt", "springframework springboot springcloud");
        Map<Integer, Double> scores = regexSearch.search("spring.*");
        assertFalse(scores.isEmpty());
        assertTrue(scores.values().iterator().next() > 0);
    }

    @Test
    void alternationMatchesEitherOption() {
        index("/a.txt", "redis caching");
        index("/b.txt", "memcached performance");
        Map<Integer, Double> scores = regexSearch.search("redis|memcached");
        assertEquals(2, scores.size());
    }

    @Test
    void optionalGroupPattern() {
        index("/a.txt", "docker");
        index("/b.txt", "dockerfile");
        Map<Integer, Double> scores = regexSearch.search("docker(file)?");
        assertEquals(2, scores.size());
    }

    @Test
    void characterClassPattern() {
        index("/a.txt", "java11 java17 java21");
        // Matches java followed by digits
        Map<Integer, Double> scores = regexSearch.search("java\\d+");
        assertEquals(1, scores.size());
    }

    @Test
    void returnsEmptyForNonMatchingPattern() {
        index("/a.txt", "redis spring docker");
        assertTrue(regexSearch.search("xyz.*").isEmpty());
    }

    @Test
    void returnsEmptyForEmptyIndex() {
        assertTrue(regexSearch.search("redis").isEmpty());
    }

    // -------------------------------------------------------
    // invalid pattern handling
    // -------------------------------------------------------

    @Test
    void invalidPatternReturnsEmpty() {
        index("/a.txt", "redis spring");
        Map<Integer, Double> scores = regexSearch.search("[invalid");
        assertTrue(scores.isEmpty(),
                "Invalid regex must return empty map rather than throw");
    }

    @Test
    void invalidPatternDoesNotThrow() {
        index("/a.txt", "redis spring");
        assertDoesNotThrow(() -> regexSearch.search("[[[invalid"));
    }

    @Test
    void unclosedParenthesisIsInvalid() {
        index("/a.txt", "redis spring");
        assertTrue(regexSearch.search("spring(boot").isEmpty());
    }

    // -------------------------------------------------------
    // case insensitivity
    // -------------------------------------------------------

    @Test
    void matchingIsCaseInsensitive() {
        // Tokenizer lowercases "Spring" → "spring" at index time
        index("/a.txt", "Spring Boot");
        Map<Integer, Double> scores = regexSearch.search("SPRING");
        assertEquals(1, scores.size());
    }

    @Test
    void upperCasePatternMatchesLowercaseToken() {
        index("/a.txt", "redis caching");
        Map<Integer, Double> scores = regexSearch.search("REDIS");
        assertEquals(1, scores.size());
    }

    // -------------------------------------------------------
    // scoring
    // -------------------------------------------------------

    @Test
    void scoresArePositive() {
        index("/a.txt", "redis caching spring");
        Map<Integer, Double> scores = regexSearch.search("redis");
        scores.values().forEach(score ->
                assertTrue(score > 0, "Score must be positive"));
    }

    @Test
    void multipleMatchingTermsIncreasesScore() {
        index("/a.txt", "springframework springboot springcloud");

        Map<Integer, Double> multiMatch = regexSearch.search("spring.*");
        Map<Integer, Double> singleMatch = regexSearch.search("springframework");

        assertFalse(multiMatch.isEmpty());
        assertFalse(singleMatch.isEmpty());

        double multiScore = multiMatch.values().iterator().next();
        double singleScore = singleMatch.values().iterator().next();

        assertTrue(multiScore >= singleScore,
                "Matching more terms must not decrease the document score");
    }

    // -------------------------------------------------------
    // integration via SearchEngine with --regex flag
    // -------------------------------------------------------

    @Test
    void searchEngineRoutesToRegexOnFlag() {
        index("/a.txt", "springframework boot");

        QueryOptions opts = QueryOptions.builder("spring.*").regex(true).build();
        List<SearchResult> results = engine.search(opts);

        assertFalse(results.isEmpty());
    }

    @Test
    void regexFlagTakesPriorityOverOtherModes() {
        index("/a.txt", "spring boot");

        // Both regex and fuzzy set — regex must win (first in route())
        QueryOptions opts = QueryOptions.builder("spring.*")
                .regex(true).fuzzy(true).build();
        List<SearchResult> results = engine.search(opts);

        assertFalse(results.isEmpty());
    }

    @Test
    void regexSearchReturnsResultsWithMetadata() {
        index("/notes/spring.txt", "spring boot framework");

        QueryOptions opts = QueryOptions.builder("spring.*").regex(true).build();
        List<SearchResult> results = engine.search(opts);

        assertFalse(results.isEmpty());
        assertNotNull(results.get(0).getMetadata().getPath());
        assertTrue(results.get(0).getScore() > 0);
    }

    @Test
    void regexSearchReturnsEmptyForNoMatch() {
        index("/a.txt", "redis spring docker");

        QueryOptions opts = QueryOptions.builder("xyz.*").regex(true).build();
        assertTrue(engine.search(opts).isEmpty());
    }

    @Test
    void invalidRegexViaSearchEngineReturnsEmpty() {
        index("/a.txt", "redis spring");

        QueryOptions opts = QueryOptions.builder("[invalid").regex(true).build();
        assertDoesNotThrow(() -> {
            List<SearchResult> results = engine.search(opts);
            assertTrue(results.isEmpty());
        });
    }
}
