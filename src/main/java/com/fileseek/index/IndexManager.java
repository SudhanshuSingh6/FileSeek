package com.fileseek.index;

import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import com.fileseek.model.FileMetadata;
import com.fileseek.scanner.DirectoryScanner;
import com.fileseek.scanner.ScanResult;
import com.fileseek.storage.CorruptionChecker;
import com.fileseek.storage.IndexDeserializer;
import com.fileseek.storage.IndexSerializer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public class IndexManager {

    private static final Path INDEX_FILE =
            Path.of(ConfigManager.getIndexDir(), "fileseek.idx");

    private final DocumentStore documentStore = new DocumentStore();
    private final InvertedIndex invertedIndex = new InvertedIndex();
    private final DirectoryScanner scanner = new DirectoryScanner();

    public void save() {
        try {
            new IndexSerializer(INDEX_FILE)
                    .serialize(documentStore, invertedIndex);
        } catch (IOException e) {
            System.err.println("[error] Failed to save index: " + e.getMessage());
        }
    }

    public void load() {
        if (!INDEX_FILE.toFile().exists()) return;

        if (CorruptionChecker.isCorrupted(INDEX_FILE)) {
            CorruptionChecker.deleteCorrupted(INDEX_FILE);
            clear();
            return;
        }

        try {
            new IndexDeserializer(INDEX_FILE)
                    .deserialize(documentStore, invertedIndex);
            System.out.printf("[info] Index loaded — %d documents, %d terms%n",
                    documentStore.size(), invertedIndex.termCount());
        } catch (IOException e) {
            System.err.println("[error] Failed to load index: " + e.getMessage());
            System.err.println("[info] Resetting to empty index.");
            clear();
        }
    }

    public static boolean indexExists() {
        return INDEX_FILE.toFile().exists();
    }

    public static Path getIndexFile() {
        return INDEX_FILE;
    }


    public int indexDocument(FileMetadata metadata, List<String> tokens) {
        int docId = documentStore.addDocument(metadata);
        for (int position = 0; position < tokens.size(); position++) {
            invertedIndex.addPosting(tokens.get(position), docId, position);
        }
        return docId;
    }

    public ScanResult indexDirectory(
            Path dir, AppConfig config, BiConsumer<Integer, String> onProgress) {
        return scanner.scan(dir, config, this, onProgress);
    }

    public boolean removeDocument(String path) {
        Optional<Integer> docId = documentStore.getDocIdByPath(path);
        if (docId.isEmpty()) return false;
        documentStore.removeDocument(docId.get());
        invertedIndex.removeDocument(docId.get());
        return true;
    }


    public DocumentStore getDocumentStore() {
        return documentStore;
    }

    public InvertedIndex getInvertedIndex() {
        return invertedIndex;
    }

    public boolean isIndexed(String path) {
        return documentStore.containsPath(path);
    }

    public int documentCount() {
        return documentStore.size();
    }

    public int termCount() {
        return invertedIndex.termCount();
    }

    public void clear() {
        documentStore.clear();
        invertedIndex.clear();
    }
}