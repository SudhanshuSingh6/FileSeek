package com.fileseek.index;

import com.fileseek.cli.FileSeekCommand;
import com.fileseek.config.AppConfig;
import com.fileseek.config.ConfigManager;
import com.fileseek.model.FileMetadata;
import com.fileseek.scanner.DirectoryScanner;
import com.fileseek.scanner.FileParser;
import com.fileseek.scanner.ScanResult;
import com.fileseek.storage.CorruptionChecker;
import com.fileseek.storage.IndexDeserializer;
import com.fileseek.storage.IndexSerializer;
import com.fileseek.util.Tokenizer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public class IndexManager {

    private static final Path INDEX_FILE =
            Path.of(ConfigManager.getIndexDir(), "fileseek.idx");

    private final DocumentStore documentStore = new DocumentStore();
    private final InvertedIndex invertedIndex = new InvertedIndex();
    private final DirectoryScanner scanner = new DirectoryScanner();
    private final FileParser fileParser = new FileParser();

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

        long startMs = System.currentTimeMillis();
        try {
            new IndexDeserializer(INDEX_FILE)
                    .deserialize(documentStore, invertedIndex);

            long loadMs = System.currentTimeMillis() - startMs;
            if (FileSeekCommand.verbose) {
                System.out.printf(
                        "  [verbose] Index loaded in %dms — "
                                + "%,d documents, %,d terms, file: %s%n",
                        loadMs, documentStore.size(), invertedIndex.termCount(),
                        INDEX_FILE.getFileName());
            }
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("version")) {
                System.err.println(
                        "[info] Index format updated (v1 → v2).\n"
                                + "       Run 'fileseek add <directory>' to rebuild.");
            } else {
                System.err.printf(
                        "[error] Failed to load index: %s%n"
                                + "        Run 'fileseek add <directory>' to rebuild.%n",
                        e.getMessage());
            }
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
        metadata.setTokenCount(tokens.size());
        int docId = documentStore.addDocument(metadata);
        for (int position = 0; position < tokens.size(); position++) {
            invertedIndex.addPosting(tokens.get(position), docId, position);
        }
        return docId;
    }

    public ScanResult indexDirectory(
            Path dir, AppConfig config,
            BiConsumer<Integer, String> onProgress) {
        return scanner.scan(dir, config, this, onProgress);
    }

    public boolean reindexFile(Path file, AppConfig config) {
        if (!fileParser.isSupported(file)) return false;

        String absolutePath = file.toAbsolutePath().toString();

        removeDocument(absolutePath);

        try {
            BasicFileAttributes attrs =
                    Files.readAttributes(file, BasicFileAttributes.class);
            long sizeBytes = attrs.size();
            String ext = FileParser.extension(file);

            FileMetadata metadata = new FileMetadata(
                    0, absolutePath, file.getFileName().toString(),
                    ext, sizeBytes, attrs.lastModifiedTime().toMillis());

            List<String> tokens;

            if (isLargeFile(ext, sizeBytes, config)) {
                tokens = Tokenizer.tokenizeFilename(file.getFileName().toString());
            } else {
                var content = fileParser.parse(file);
                if (content.isEmpty()) return false;
                tokens = Tokenizer.tokenize(content.get());
                tokens.addAll(Tokenizer.tokenizeFilename(file.getFileName().toString()));
            }

            indexDocument(metadata, tokens);
            return true;

        } catch (IOException e) {
            System.err.printf("[warn] Could not re-index %s: %s%n",
                    file.getFileName(), e.getMessage());
            return false;
        }
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

    public boolean isIndexed(String p) {
        return documentStore.containsPath(p);
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

    private boolean isLargeFile(String ext, long sizeBytes, AppConfig config) {
        return ext.equals(".pdf")
                ? sizeBytes > config.getMaxPdfFileSizeBytes()
                : sizeBytes > config.getMaxTextFileSizeBytes();
    }
}