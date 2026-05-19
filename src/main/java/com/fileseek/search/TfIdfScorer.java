package com.fileseek.search;


public class TfIdfScorer {

    public double tf(int frequency) {
        if (frequency <= 0) return 0.0;
        return 1.0 + Math.log(frequency);
    }

    public double idf(int totalDocs, int docFrequency) {
        if (docFrequency <= 0 || totalDocs <= 0) return 0.0;
        return Math.log((double) totalDocs / docFrequency);
    }

    public double score(int frequency, int totalDocs, int docFrequency) {
        return tf(frequency) * idf(totalDocs, docFrequency);
    }
}