package com.fileseek.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenizerTest {

    // -------------------------------------------------------
    // tokenize() — standard mode
    // -------------------------------------------------------

    @Test
    void basicTokenization() {
        List<String> tokens = Tokenizer.tokenize("Redis caching");
        assertEquals(List.of("redis", "caching"), tokens);
    }

    @Test
    void lowercasesAllTokens() {
        List<String> tokens = Tokenizer.tokenize("Spring BOOT Framework");
        assertEquals(List.of("spring", "boot", "framework"), tokens);
    }

    @Test
    void splitOnNonAlphanumeric() {
        List<String> tokens = Tokenizer.tokenize("hello-world_test.file");
        assertEquals(List.of("hello", "world", "test", "file"), tokens);
    }

    @Test
    void removesEmptyTokens() {
        List<String> tokens = Tokenizer.tokenize("  multiple   spaces  ");
        assertFalse(tokens.contains(""));
        assertFalse(tokens.isEmpty());
    }

    @Test
    void removesStopWords() {
        List<String> tokens = Tokenizer.tokenize("the quick brown fox");
        assertFalse(tokens.contains("the"));
        assertTrue(tokens.contains("quick"));
        assertTrue(tokens.contains("brown"));
        assertTrue(tokens.contains("fox"));
    }

    @Test
    void removesMultipleStopWords() {
        List<String> tokens = Tokenizer.tokenize("this is a test");
        assertFalse(tokens.contains("this"));
        assertFalse(tokens.contains("is"));
        assertFalse(tokens.contains("a"));
        assertTrue(tokens.contains("test"));
    }

    @Test
    void handlesAllStopWords() {
        List<String> tokens = Tokenizer.tokenize("the and or but");
        assertTrue(tokens.isEmpty());
    }

    @Test
    void handlesNullInput() {
        List<String> tokens = Tokenizer.tokenize(null);
        assertTrue(tokens.isEmpty());
    }

    @Test
    void handlesEmptyString() {
        List<String> tokens = Tokenizer.tokenize("");
        assertTrue(tokens.isEmpty());
    }

    @Test
    void handlesBlankString() {
        List<String> tokens = Tokenizer.tokenize("   ");
        assertTrue(tokens.isEmpty());
    }

    @Test
    void handlesSingleWord() {
        List<String> tokens = Tokenizer.tokenize("docker");
        assertEquals(List.of("docker"), tokens);
    }

    @Test
    void handlesPunctuationHeavyInput() {
        List<String> tokens = Tokenizer.tokenize("hello, world! (test) - ok.");
        assertEquals(List.of("hello", "world", "test", "ok"), tokens);
    }

    @Test
    void handlesNumbers() {
        List<String> tokens = Tokenizer.tokenize("version 2 release");
        assertTrue(tokens.contains("2"));
        assertTrue(tokens.contains("version"));
        assertTrue(tokens.contains("release"));
    }

    @Test
    void handlesAlphanumericMixed() {
        List<String> tokens = Tokenizer.tokenize("java11 spring6");
        assertEquals(List.of("java11", "spring6"), tokens);
    }

    // -------------------------------------------------------
    // tokenizePhrase() — phrase mode
    // -------------------------------------------------------

    @Test
    void phraseModeKeepsStopWords() {
        List<String> tokens = Tokenizer.tokenizePhrase("lord of the rings");
        assertTrue(tokens.contains("of"), "phrase mode must keep 'of'");
        assertTrue(tokens.contains("the"), "phrase mode must keep 'the'");
        assertTrue(tokens.contains("lord"));
        assertTrue(tokens.contains("rings"));
    }

    @Test
    void phraseModeStillLowercases() {
        List<String> tokens = Tokenizer.tokenizePhrase("Spring Boot");
        assertEquals(List.of("spring", "boot"), tokens);
    }

    @Test
    void phraseModeStillSplitsOnNonAlphanumeric() {
        List<String> tokens = Tokenizer.tokenizePhrase("hello-world");
        assertEquals(List.of("hello", "world"), tokens);
    }

    @Test
    void phraseModeHandlesNull() {
        List<String> tokens = Tokenizer.tokenizePhrase(null);
        assertTrue(tokens.isEmpty());
    }

    @Test
    void standardAndPhraseProduceDifferentResults() {
        String input = "lord of the rings";
        List<String> standard = Tokenizer.tokenize(input);
        List<String> phrase = Tokenizer.tokenizePhrase(input);
        assertNotEquals(standard, phrase);
        assertFalse(standard.contains("of"));
        assertTrue(phrase.contains("of"));
    }

    // -------------------------------------------------------
    // tokenizeFilename()
    // -------------------------------------------------------

    @Test
    void filenameStripsExtension() {
        List<String> tokens = Tokenizer.tokenizeFilename("backend-guide.md");
        assertFalse(tokens.contains("md"));
        assertTrue(tokens.contains("backend"));
        assertTrue(tokens.contains("guide"));
    }

    @Test
    void filenameSplitsOnSeparators() {
        List<String> tokens = Tokenizer.tokenizeFilename("my_project_notes.txt");
        assertEquals(List.of("my", "project", "notes"), tokens);
    }

    @Test
    void filenameKeepsStopWords() {
        List<String> tokens = Tokenizer.tokenizeFilename("the-config.yml");
        assertTrue(tokens.contains("the"));
    }

    @Test
    void filenameHandlesNoExtension() {
        List<String> tokens = Tokenizer.tokenizeFilename("Makefile");
        assertEquals(List.of("makefile"), tokens);
    }

    @Test
    void filenameHandlesNull() {
        List<String> tokens = Tokenizer.tokenizeFilename(null);
        assertTrue(tokens.isEmpty());
    }
}
