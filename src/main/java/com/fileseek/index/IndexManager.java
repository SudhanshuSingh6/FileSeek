package com.fileseek.index;

import com.fileseek.model.FileMetadata;
import com.fileseek.model.Posting;

import java.util.List;
import java.util.Optional;

public class IndexManager {

    private final DocumentStore  documentStore  = new DocumentStore();
    private final InvertedIndex  invertedIndex  = new InvertedIndex();

    public DocumentStore  getDocumentStore() { return documentStore; }
    public InvertedIndex  getInvertedIndex() { return invertedIndex; }

    public int indexDocument(FileMetadata metadata, List<String> tokens) {
        int docId = documentStore.addDocument(metadata);

        for (int position = 0; position < tokens.size(); position++) {
            invertedIndex.addPosting(tokens.get(position), docId, position);
        }

        return docId;
    }

    public boolean removeDocument(String path) {
        Optional<Integer> docId = documentStore.getDocIdByPath(path);
        if (docId.isEmpty()) return false;

        documentStore.removeDocument(docId.get());
        invertedIndex.removeDocument(docId.get());
        return true;
    }

    public boolean isIndexed(String path) {
        return documentStore.containsPath(path);
    }

    public int documentCount() { return documentStore.size(); }
    public int termCount()     { return invertedIndex.termCount(); }

    public void clear() {
        documentStore.clear();
        invertedIndex.clear();
    }
}