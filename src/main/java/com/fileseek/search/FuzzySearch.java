package com.fileseek.search;

import com.fileseek.index.DocumentStore;
import com.fileseek.index.InvertedIndex;
import com.fileseek.model.Posting;

import java.util.*;

public class FuzzySearch {

    static final int MAX_EDIT_DISTANCE = 2;

    private static final double[] DISTANCE_MULTIPLIER = {1.0, 0.75, 0.5};
    private static final double FILENAME_BOOST = 3.0;

    private final InvertedIndex invertedIndex;
    private final DocumentStore documentStore;
    private final BM25Scorer scorer;

    public FuzzySearch(InvertedIndex invertedIndex,
                       DocumentStore documentStore,
                       BM25Scorer scorer) {
        this.invertedIndex = invertedIndex;
        this.documentStore = documentStore;
        this.scorer = scorer;
    }

    public Map<Integer, Double> search(List<String> queryTerms) {
        Map<Integer, Double> scores = new HashMap<>();
        int totalDocs = documentStore.size();

        for (String queryTerm : queryTerms) {
            List<FuzzyMatch> candidates = findCandidates(queryTerm);

            for (FuzzyMatch match : candidates) {
                List<Posting> postings = invertedIndex.getPostings(match.term());
                int df = postings.size();
                double distMultiplier = DISTANCE_MULTIPLIER[match.distance()];

                for (Posting posting : postings) {
                    // BM25 score — passes docId so scorer can look up doc length
                    double termScore = scorer.score(
                            posting.frequency(), posting.docId(), totalDocs, df);
                    termScore *= distMultiplier;

                    double finalTermScore = termScore;
                    documentStore.getDocument(posting.docId()).ifPresent(meta -> {
                        double boost = meta.getFileName().toLowerCase()
                                .contains(match.term()) ? FILENAME_BOOST : 1.0;
                        scores.merge(posting.docId(), finalTermScore * boost, Double::sum);
                    });
                }
            }
        }
        return scores;
    }

    private List<FuzzyMatch> findCandidates(String queryTerm) {
        List<FuzzyMatch> candidates = new ArrayList<>();

        for (String indexTerm : invertedIndex.getAllTerms()) {
            // Length pre-filter — skip if gap exceeds max distance
            if (Math.abs(indexTerm.length() - queryTerm.length()) > MAX_EDIT_DISTANCE) {
                continue;
            }

            int distance = levenshtein(queryTerm, indexTerm, MAX_EDIT_DISTANCE);
            if (distance <= MAX_EDIT_DISTANCE) {
                candidates.add(new FuzzyMatch(indexTerm, distance));
            }
        }

        return candidates;
    }

    static int levenshtein(String a, String b, int maxDistance) {
        if (Math.abs(a.length() - b.length()) > maxDistance) return maxDistance + 1;
        if (a.equals(b)) return 0;
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();

        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];

        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            int rowMin = curr[0];

            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(
                        prev[j] + 1,
                        Math.min(
                                curr[j - 1] + 1,
                                prev[j - 1] + cost));
                rowMin = Math.min(rowMin, curr[j]);
            }

            if (rowMin > maxDistance) return maxDistance + 1;

            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        return prev[b.length()];
    }


    record FuzzyMatch(String term, int distance) {
    }
}