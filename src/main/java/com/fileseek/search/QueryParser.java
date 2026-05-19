package com.fileseek.search;

import com.fileseek.util.Tokenizer;

import java.util.List;

public class QueryParser {

    public static ParsedQuery parse(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return new ParsedQuery(List.of(), false);
        }

        String trimmed = rawQuery.trim();

        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")
                && trimmed.length() > 2) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            return new ParsedQuery(Tokenizer.tokenizePhrase(inner), true);
        }

        return new ParsedQuery(Tokenizer.tokenize(trimmed), false);
    }

    public static class ParsedQuery {
        private final List<String> terms;
        private final boolean phrase;

        public ParsedQuery(List<String> terms, boolean phrase) {
            this.terms = terms;
            this.phrase = phrase;
        }

        public List<String> getTerms() {
            return terms;
        }

        public boolean isPhrase() {
            return phrase;
        }

        public boolean isEmpty() {
            return terms.isEmpty();
        }
    }
}