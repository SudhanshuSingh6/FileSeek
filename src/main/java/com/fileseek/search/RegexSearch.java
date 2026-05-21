package com.fileseek.search;

import com.fileseek.index.DocumentStore;
import com.fileseek.index.InvertedIndex;
import com.fileseek.model.Posting;

import java.util.*;
import java.util.regex.*;

public class RegexSearch {

    private static final double FILENAME_BOOST = 3.0;

    private final InvertedIndex invertedIndex;
    private final DocumentStore documentStore;
    private final BM25Scorer scorer;

    public RegexSearch(InvertedIndex invertedIndex,
                       DocumentStore documentStore,
                       BM25Scorer scorer) {
        this.invertedIndex = invertedIndex;
        this.documentStore = documentStore;
        this.scorer = scorer;
    }

    public Map<Integer, Double> search(String rawPattern) {
        Pattern pattern = compile(rawPattern);
        if (pattern == null) return Map.of();

        Map<Integer, Double> scores = new HashMap<>();
        int totalDocs = documentStore.size();

        for (String term : invertedIndex.getAllTerms()) {
            if (!pattern.matcher(term).matches()) continue;

            List<Posting> postings = invertedIndex.getPostings(term);
            int df = postings.size();

            for (Posting posting : postings) {
                double termScore = scorer.score(
                        posting.frequency(), posting.docId(), totalDocs, df);

                documentStore.getDocument(posting.docId()).ifPresent(meta -> {
                    double boost = meta.getFileName().toLowerCase()
                            .contains(term) ? FILENAME_BOOST : 1.0;
                    scores.merge(posting.docId(), termScore * boost, Double::sum);
                });
            }
        }

        return scores;
    }

    private Pattern compile(String rawPattern) {
        try {
            return Pattern.compile(rawPattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            System.err.printf(
                    "[error] Invalid regex pattern \"%s\": %s%n"
                            + "        Check your pattern syntax and try again.%n",
                    rawPattern, e.getDescription());
            return null;
        }
    }
}