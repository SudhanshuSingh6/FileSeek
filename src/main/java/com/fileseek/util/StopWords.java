package com.fileseek.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class StopWords {

    private static final Set<String> WORDS = load();

    private StopWords() {}

    public static boolean contains(String token) {
        return WORDS.contains(token);
    }

    public static Set<String> all() {
        return Collections.unmodifiableSet(WORDS);
    }

    private static Set<String> load() {
        Set<String> words = new HashSet<>();
        try (InputStream is = StopWords.class.getResourceAsStream("/stopwords.txt")) {
            if (is == null) {
                System.err.println("[warn] stopwords.txt not found on classpath");
                return words;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String word = line.strip();
                    if (!word.isEmpty() && !word.startsWith("#")) {
                        words.add(word);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[warn] Could not load stopwords: " + e.getMessage());
        }
        return words;
    }
}