package com.fileseek.index;

import com.fileseek.model.Posting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InvertedIndex {

    private final Map<String, List<Posting>> index = new ConcurrentHashMap<>();

    public void addPosting(String term, int docId, int position) {
        List<Posting> postings = index.computeIfAbsent(term, k -> new ArrayList<>());

        for (Posting p : postings) {
            if (p.docId() == docId) {
                p.addPosition(position);
                return;
            }
        }

        Posting posting = new Posting(docId);
        posting.addPosition(position);
        postings.add(posting);
    }

    public List<Posting> getPostings(String term) {
        return Collections.unmodifiableList(
                index.getOrDefault(term, Collections.emptyList())
        );
    }

    public boolean containsTerm(String term) {
        return index.containsKey(term);
    }

    public Set<String> getAllTerms() {
        return Collections.unmodifiableSet(index.keySet());
    }

    public void removeDocument(int docId) {
        for (Map.Entry<String, List<Posting>> entry : index.entrySet()) {
            entry.getValue().removeIf(p -> p.docId() == docId);
        }
        index.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public int termCount() {
        return index.size();
    }

    public int documentFrequency(String term) {
        return index.getOrDefault(term, Collections.emptyList()).size();
    }

    public void clear() {
        index.clear();
    }

    public void restorePostings(String term, List<Posting> postings) {
        index.put(term, new ArrayList<>(postings));
    }
}