package com.fileseek.model;

public class SearchResult implements Comparable<SearchResult> {

    private final FileMetadata metadata;
    private final double score;
    private String snippet;
    private long searchDurationMs;

    public SearchResult(FileMetadata metadata, double score) {
        this.metadata = metadata;
        this.score = score;
        this.snippet = "";
    }

    public FileMetadata getMetadata() {
        return metadata;
    }

    public double getScore() {
        return score;
    }

    public String getSnippet() {
        return snippet;
    }

    public long getSearchDurationMs() {
        return searchDurationMs;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public void setSearchDurationMs(long durationMs) {
        this.searchDurationMs = durationMs;
    }

    @Override
    public int compareTo(SearchResult other) {
        return Double.compare(other.score, this.score);
    }
}