package com.fileseek.util;

import java.util.ArrayList;
import java.util.List;

public class Tokenizer {

    public static List<String> tokenize(String text) {
        return process(text, false);
    }

    public static List<String> tokenizePhrase(String text) {
        return process(text, true);
    }

    public static List<String> tokenizeFilename(String text) {
        if (text == null || text.isBlank()) return List.of();

        int dotIndex = text.lastIndexOf('.');
        String name = (dotIndex > 0) ? text.substring(0, dotIndex) : text;

        String lowered = name.toLowerCase();
        String[] parts = lowered.split("[^a-z0-9]+");

        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static List<String> process(String text, boolean phraseMode) {
        if (text == null || text.isBlank()) return List.of();

        String lowered = text.toLowerCase();

        String[] parts = lowered.split("[^a-z0-9]+");

        List<String> tokens = new ArrayList<>();
        for (String part : parts) {

            if (part.isEmpty()) continue;
            if (!phraseMode && StopWords.contains(part)) continue;

            tokens.add(part);
        }

        return tokens;
    }
}