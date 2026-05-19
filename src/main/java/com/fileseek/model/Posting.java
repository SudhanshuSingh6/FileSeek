package com.fileseek.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Posting {

    private int docId;
    private List<Integer> positions;

    public Posting(int docId) {
        this.docId = docId;
        this.positions = new ArrayList<>();
    }

    public Posting(int docId, List<Integer> positions) {
        this.docId = docId;
        this.positions = new ArrayList<>(positions);
    }

    public void addPosition(int position) {
        positions.add(position);
    }

    public int docId() {
        return docId;
    }

    public List<Integer> positions() {
        return Collections.unmodifiableList(positions);
    }

    public int frequency() {
        return positions.size();
    }

    @Override
    public String toString() {
        return String.format("Posting{docId=%d, positions=%s}", docId, positions);
    }
}