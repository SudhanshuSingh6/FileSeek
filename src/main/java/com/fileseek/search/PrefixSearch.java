package com.fileseek.search;

import com.fileseek.index.DocumentStore;
import com.fileseek.index.InvertedIndex;
import com.fileseek.model.Posting;

import java.util.*;
import java.util.stream.Collectors;

public class PrefixSearch {

    private static final double FILENAME_BOOST = 3.0;

    private final InvertedIndex invertedIndex;
    private final DocumentStore documentStore;
    private final TfIdfScorer scorer;

    public PrefixSearch(InvertedIndex invertedIndex,
                        DocumentStore documentStore,
                        TfIdfScorer scorer) {
        this.invertedIndex = invertedIndex;
        this.documentStore = documentStore;
        this.scorer = scorer;
    }

    public Map<Integer, Double> search(List<String> prefixes) {
        Map<Integer, Double> scores = new HashMap<>();
        int totalDocs = documentStore.size();

        for (String prefix : prefixes) {
            List<String> matchingTerms = findMatchingTerms(prefix);

            for (String term : matchingTerms) {
                List<Posting> postings = invertedIndex.getPostings(term);
                int df = postings.size();
                double idf = scorer.idf(totalDocs, df);

                double coverageBoost = (double) prefix.length() / term.length();

                for (Posting posting : postings) {
                    double tf = scorer.tf(posting.frequency());
                    double termScore = tf * idf * coverageBoost;

                    documentStore.getDocument(posting.docId()).ifPresent(meta -> {
                        double filenameBoost = meta.getFileName().toLowerCase()
                                .contains(term) ? FILENAME_BOOST : 1.0;
                        scores.merge(posting.docId(), termScore * filenameBoost,
                                Double::sum);
                    });
                }
            }
        }

        return scores;
    }

    List<String> findMatchingTerms(String prefix) {
        if (prefix == null || prefix.isBlank()) return List.of();
        return invertedIndex.getAllTerms().stream()
                .filter(term -> term.startsWith(prefix))
                .collect(Collectors.toList());
    }
}