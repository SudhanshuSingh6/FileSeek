package com.fileseek.search;

import com.fileseek.index.DocumentStore;
import com.fileseek.model.FileMetadata;


public class BM25Scorer {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final DocumentStore documentStore;

    private double cachedAvgLen = -1;

    public BM25Scorer(DocumentStore documentStore) {
        this.documentStore = documentStore;
    }


    public double score(int freq, int docId, int totalDocs, int docFreq) {
        if (freq <= 0 || docFreq <= 0 || totalDocs <= 0) return 0.0;

        double avgLen = averageDocumentLength();
        int docLen = documentStore.getDocument(docId)
                .map(FileMetadata::getTokenCount)
                .filter(n -> n > 0)
                .orElse((int) Math.max(1, avgLen));

        double idf = Math.log(
                (totalDocs - docFreq + 0.5) / (docFreq + 0.5) + 1.0);

        double normalizedTf = (freq * (K1 + 1.0))
                / (freq + K1 * (1.0 - B + B * (docLen / avgLen)));

        return idf * normalizedTf;
    }

    public void invalidateCache() {
        cachedAvgLen = -1;
    }

    private double averageDocumentLength() {
        if (cachedAvgLen >= 0) return cachedAvgLen;
        cachedAvgLen = documentStore.averageDocumentLength();
        return cachedAvgLen;
    }
}