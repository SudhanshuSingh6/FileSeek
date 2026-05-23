package com.fileseek.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueryParserTest {

    // --- phrase detection ---

    @Test
    void detectsPhraseQuery() {
        var result = QueryParser.parse("\"spring boot\"");
        assertTrue(result.isPhrase());
    }

    @Test
    void detectsRegularQuery() {
        var result = QueryParser.parse("spring boot");
        assertFalse(result.isPhrase());
    }

    @Test
    void unclosedOpenQuoteIsNotPhrase() {
        var result = QueryParser.parse("\"spring boot");
        assertFalse(result.isPhrase());
    }

    @Test
    void unclosedCloseQuoteIsNotPhrase() {
        var result = QueryParser.parse("spring boot\"");
        assertFalse(result.isPhrase());
    }

    @Test
    void singleQuoteMarkIsEmpty() {
        var result = QueryParser.parse("\"");
        assertTrue(result.isEmpty());
    }

    @Test
    void emptyQuotesIsEmpty() {
        // Two quotes with nothing inside
        var result = QueryParser.parse("\"\"");
        assertTrue(result.isEmpty());
    }

    // --- phrase tokenization ---

    @Test
    void phraseQueryKeepsStopWords() {
        var result = QueryParser.parse("\"lord of the rings\"");
        assertTrue(result.isPhrase());
        assertTrue(result.getTerms().contains("of"),
                "Phrase mode must keep stop word 'of'");
        assertTrue(result.getTerms().contains("the"),
                "Phrase mode must keep stop word 'the'");
    }

    @Test
    void phraseQueryLowercases() {
        var result = QueryParser.parse("\"Spring Boot\"");
        assertEquals(List.of("spring", "boot"), result.getTerms());
    }

    @Test
    void phraseQuerySplitsOnNonAlphanumeric() {
        var result = QueryParser.parse("\"hello-world\"");
        assertEquals(List.of("hello", "world"), result.getTerms());
    }

    @Test
    void singleWordPhrase() {
        var result = QueryParser.parse("\"redis\"");
        assertTrue(result.isPhrase());
        assertEquals(List.of("redis"), result.getTerms());
    }

    // --- keyword tokenization ---

    @Test
    void keywordQueryRemovesStopWords() {
        var result = QueryParser.parse("the quick fox");
        assertFalse(result.getTerms().contains("the"));
        assertTrue(result.getTerms().contains("quick"));
        assertTrue(result.getTerms().contains("fox"));
    }

    @Test
    void keywordQueryLowercases() {
        var result = QueryParser.parse("Redis Caching");
        assertEquals(List.of("redis", "caching"), result.getTerms());
    }

    @Test
    void multipleKeywords() {
        var result = QueryParser.parse("redis caching spring boot");
        assertEquals(4, result.getTerms().size());
    }

    @Test
    void keywordQuerySplitsOnPunctuation() {
        var result = QueryParser.parse("hello-world_test");
        assertTrue(result.getTerms().contains("hello"));
        assertTrue(result.getTerms().contains("world"));
        assertTrue(result.getTerms().contains("test"));
    }

    // --- edge cases ---

    @Test
    void nullQueryIsEmpty() {
        var result = QueryParser.parse(null);
        assertTrue(result.isEmpty());
        assertFalse(result.isPhrase());
    }

    @Test
    void emptyStringIsEmpty() {
        var result = QueryParser.parse("");
        assertTrue(result.isEmpty());
    }

    @Test
    void blankStringIsEmpty() {
        var result = QueryParser.parse("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    void allStopWordsProducesEmptyTermList() {
        var result = QueryParser.parse("the and or but");
        assertTrue(result.getTerms().isEmpty());
        assertFalse(result.isPhrase());
    }

    @Test
    void singleWordQuery() {
        var result = QueryParser.parse("redis");
        assertEquals(List.of("redis"), result.getTerms());
        assertFalse(result.isPhrase());
    }

    // --- isEmpty ---

    @Test
    void isEmptyReturnsTrueForEmptyTermList() {
        var result = QueryParser.parse("the and");
        assertTrue(result.isEmpty());
    }

    @Test
    void isEmptyReturnsFalseForNonEmptyTermList() {
        var result = QueryParser.parse("redis caching");
        assertFalse(result.isEmpty());
    }

    // --- phrase vs keyword diverge on stop words ---

    @Test
    void phraseAndKeywordProduceDifferentResultsForStopWordInput() {
        String raw = "lord of the rings";

        var keyword = QueryParser.parse(raw);
        var phrase = QueryParser.parse("\"" + raw + "\"");

        assertFalse(keyword.getTerms().contains("of"));
        assertTrue(phrase.getTerms().contains("of"),
                "phrase mode must preserve stop words");
        assertNotEquals(keyword.getTerms(), phrase.getTerms());
    }
}
