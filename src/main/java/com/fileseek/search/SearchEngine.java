package com.fileseek.search;

import com.fileseek.cli.FileSeekCommand;
import com.fileseek.index.DocumentStore;
import com.fileseek.index.IndexManager;
import com.fileseek.index.InvertedIndex;
import com.fileseek.model.FileMetadata;
import com.fileseek.model.Posting;
import com.fileseek.model.QueryOptions;
import com.fileseek.model.SearchResult;

import java.util.*;
import java.util.stream.Collectors;

public class SearchEngine {

    private static final double FILENAME_BOOST = 3.0;
    private static final double PHRASE_BOOST = 2.0;
    private static final int MAX_SNIPPETS = 10;

    private final InvertedIndex invertedIndex;
    private final DocumentStore documentStore;
    private final SnippetExtractor snippetExtractor;
    private final FuzzySearch fuzzySearch;
    private final PrefixSearch prefixSearch;
    private final BM25Scorer scorer;
    private final RegexSearch regexSearch;

    public SearchEngine(IndexManager indexManager) {
        this.invertedIndex = indexManager.getInvertedIndex();
        this.documentStore = indexManager.getDocumentStore();
        this.scorer = new BM25Scorer(documentStore);
        this.snippetExtractor = new SnippetExtractor();
        this.fuzzySearch = new FuzzySearch(invertedIndex, documentStore, scorer);
        this.prefixSearch = new PrefixSearch(invertedIndex, documentStore, scorer);
        this.regexSearch = new RegexSearch(invertedIndex, documentStore, scorer);
    }

    public List<SearchResult> search(QueryOptions options) {
        long startMs = System.currentTimeMillis();

        QueryParser.ParsedQuery query = QueryParser.parse(options.getRawQuery());
        if (query.isEmpty()) return List.of();

        Map<Integer, Double> scores = route(query, options);
        if (scores.isEmpty()) return List.of();

        if (FileSeekCommand.verbose) {
            System.out.printf("  [verbose] Query tokens : %s%n", query.getTerms());
            System.out.printf("  [verbose] Search mode  : %s%n", query.isPhrase() ? "phrase" : options.isFuzzy() ? "fuzzy" : options.isPrefix() ? "prefix" : options.isRegex() ? "regex" : "keyword");
            System.out.printf("  [verbose] Candidates   : %,d documents%n", scores.size());
        }

        List<SearchResult> results = new ArrayList<>();

        for (Map.Entry<Integer, Double> entry : scores.entrySet()) {
            documentStore.getDocument(entry.getKey()).ifPresent(meta -> {
                if (passesFilters(meta, options)) {
                    results.add(new SearchResult(meta, entry.getValue()));
                }
            });
        }

        Collections.sort(results);

        if (FileSeekCommand.verbose) {
            System.out.printf("  [verbose] After filters: %,d documents%n", results.size());
            System.out.printf("  [verbose] Top score    : %.4f%n", results.isEmpty() ? 0.0 : results.get(0).getScore());
        }
        List<String> displayTerms = query.getTerms();
        int snippetCount = Math.min(results.size(), MAX_SNIPPETS);
        for (int i = 0; i < snippetCount; i++) {
            String snippet = snippetExtractor.extract(results.get(i).getMetadata(), displayTerms);

            results.get(i).setSnippet(snippet);
        }
        long durationMs = System.currentTimeMillis() - startMs;

        results.forEach(r -> r.setSearchDurationMs(durationMs));

        return results;
    }

    private Map<Integer, Double> route(QueryParser.ParsedQuery query, QueryOptions options) {
        if (options.isRegex()) {
            return regexSearch.search(options.getRawQuery());
        }
        if (query.isPhrase()) {
            return phraseSearch(query.getTerms());
        }
        if (options.isFuzzy()) {
            return fuzzySearch.search(query.getTerms());
        }
        if (options.isPrefix()) {
            return prefixSearch.search(query.getTerms());
        }
        return keywordSearch(query.getTerms());
    }

    private Map<Integer, Double> keywordSearch(List<String> terms) {
        Map<Integer, Double> scores = new HashMap<>();
        int totalDocs = documentStore.size();

        for (String term : terms) {
            List<Posting> postings = invertedIndex.getPostings(term);

            int df = postings.size();
            if (df == 0) continue;
            for (Posting posting : postings) {

                double termScore = scorer.score(posting.frequency(), posting.docId(), totalDocs, df);

                documentStore.getDocument(posting.docId()).ifPresent(meta -> {

                    double boost = filenameBoost(meta.getFileName(), term);

                    scores.merge(posting.docId(), termScore * boost, Double::sum);
                });
            }
        }

        return scores;
    }

    private Map<Integer, Double> phraseSearch(List<String> terms) {

        Map<Integer, Double> scores = new HashMap<>();

        Set<Integer> candidates = null;

        for (String term : terms) {

            Set<Integer> docsWithTerm = invertedIndex.getPostings(term).stream().map(Posting::docId).collect(Collectors.toSet());

            candidates = (candidates == null) ? new HashSet<>(docsWithTerm) : intersect(candidates, docsWithTerm);

            if (candidates.isEmpty()) {
                return scores;
            }
        }

        int totalDocs = documentStore.size();

        for (int docId : candidates) {

            if (!hasConsecutivePhrase(terms, docId)) {
                continue;
            }
            double docScore = 0;

            for (String term : terms) {
                int df = invertedIndex.documentFrequency(term);
                for (Posting p : invertedIndex.getPostings(term)) {
                    if (p.docId() == docId) {

                        docScore += scorer.score(p.frequency(), p.docId(), totalDocs, df);

                        break;
                    }
                }
            }
            scores.put(docId, docScore * PHRASE_BOOST);
        }

        return scores;
    }

    private boolean hasConsecutivePhrase(List<String> terms, int docId) {

        List<Set<Integer>> positionSets = new ArrayList<>();
        for (String term : terms) {
            Set<Integer> positions = null;
            for (Posting p : invertedIndex.getPostings(term)) {
                if (p.docId() == docId) {
                    positions = new HashSet<>(p.positions());
                    break;
                }
            }
            if (positions == null) {
                return false;
            }
            positionSets.add(positions);
        }

        for (int startPos : positionSets.get(0)) {
            boolean match = true;
            for (int i = 1; i < terms.size(); i++) {

                if (!positionSets.get(i).contains(startPos + i)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }

        return false;
    }

    private boolean passesFilters(FileMetadata meta, QueryOptions options) {
        if (options.hasExtFilter() && !meta.getExtension().equalsIgnoreCase(options.getFilterExt())) {
            return false;
        }
        if (options.hasSizeFilter() && meta.getSizeBytes() < options.getMinSizeBytes()) {
            return false;
        }
        return !options.hasDateFilter() || meta.getLastModified() >= options.getModifiedAfterEpoch();
    }

    private double filenameBoost(String fileName, String term) {
        return fileName.toLowerCase().contains(term) ? FILENAME_BOOST : 1.0;
    }

    private Set<Integer> intersect(Set<Integer> a, Set<Integer> b) {
        Set<Integer> result = new HashSet<>(a);
        result.retainAll(b);
        return result;
    }
}