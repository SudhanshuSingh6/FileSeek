package com.fileseek.model;

public class QueryOptions {

    private final String rawQuery;
    private final boolean fuzzy;
    private final boolean prefix;
    private final boolean phrase;
    private String filterExt;
    private Long minSizeBytes;
    private Long modifiedAfterEpoch;
    private final boolean regex;

    private QueryOptions(Builder builder) {
        this.rawQuery = builder.rawQuery;
        this.fuzzy = builder.fuzzy;
        this.prefix = builder.prefix;
        this.phrase = builder.phrase;
        this.filterExt = builder.filterExt;
        this.minSizeBytes = builder.minSizeBytes;
        this.modifiedAfterEpoch = builder.modifiedAfterEpoch;
        this.regex = builder.regex;

    }

    public String getRawQuery() {
        return rawQuery;
    }
    public boolean isRegex() { return regex; }

    public boolean isFuzzy() {
        return fuzzy;
    }

    public boolean isPrefix() {
        return prefix;
    }

    public boolean isPhrase() {
        return phrase;
    }

    public String getFilterExt() {
        return filterExt;
    }

    public Long getMinSizeBytes() {
        return minSizeBytes;
    }

    public Long getModifiedAfterEpoch() {
        return modifiedAfterEpoch;
    }

    public boolean hasExtFilter() {
        return filterExt != null;
    }

    public boolean hasSizeFilter() {
        return minSizeBytes != null;
    }

    public boolean hasDateFilter() {
        return modifiedAfterEpoch != null;
    }


    public static Builder builder(String rawQuery) {
        return new Builder(rawQuery);
    }

    public static class Builder {
        private final String rawQuery;
        private boolean fuzzy = false;
        private boolean prefix = false;
        private boolean phrase = false;
        private String filterExt = null;
        private Long minSizeBytes = null;
        private Long modifiedAfterEpoch = null;
        private boolean regex = false;


        public Builder(String rawQuery) {
            this.rawQuery = rawQuery;
        }

        public Builder fuzzy(boolean v) {
            this.fuzzy = v;
            return this;
        }

        public Builder prefix(boolean v) {
            this.prefix = v;
            return this;
        }

        public Builder phrase(boolean v) {
            this.phrase = v;
            return this;
        }

        public Builder filterExt(String v) {
            this.filterExt = v;
            return this;
        }

        public Builder minSizeBytes(Long v) {
            this.minSizeBytes = v;
            return this;
        }

        public Builder modifiedAfterEpoch(Long v) {
            this.modifiedAfterEpoch = v;
            return this;
        }

        public Builder regex(boolean v) {
            this.regex = v; return this;
        }

        public QueryOptions build() {
            return new QueryOptions(this);
        }
    }
}