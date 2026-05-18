package com.fileseek.scanner;

import com.fileseek.config.AppConfig;
import com.fileseek.index.IndexManager;
import com.fileseek.model.FileMetadata;
import com.fileseek.util.Tokenizer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

public class DirectoryScanner {

    private final FileParser fileParser = new FileParser();

    public ScanResult scan(
            Path root,
            AppConfig config,
            IndexManager indexManager,
            BiConsumer<Integer, String> onProgress) {

        ScanResult result = new ScanResult();
        long startMs = System.currentTimeMillis();

        try {
            Files.walkFileTree(
                    root,
                    Set.of(),              // No FOLLOW_LINKS — symlink safe
                    Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {

                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path dir, BasicFileAttributes attrs) {

                            String name = dir.getFileName().toString();

                            if (config.isIgnored(name)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (name.startsWith(".")) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }

                            result.incrementDirectories();
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(
                                Path file, BasicFileAttributes attrs) {

                            processFile(file, attrs, config, indexManager, result);

                            if (onProgress != null) {
                                onProgress.accept(result.totalProcessed(),
                                        file.toString());
                            }

                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(
                                Path file, IOException exc) {
                            result.addError(file + ": " + exc.getMessage());
                            return FileVisitResult.CONTINUE;
                        }
                    }
            );
        } catch (IOException e) {
            result.addError("Root scan failed: " + e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - startMs);
        return result;
    }

    // --- private ---

    private void processFile(
            Path file,
            BasicFileAttributes attrs,
            AppConfig config,
            IndexManager indexManager,
            ScanResult result) {

        // Skip unsupported extensions
        if (!fileParser.isSupported(file)) {
            result.incrementSkipped();
            return;
        }

        // Skip already-indexed files (incremental, Phase 9 handles changes)
        if (indexManager.isIndexed(file.toAbsolutePath().toString())) {
            result.incrementSkipped();
            return;
        }

        String ext = FileParser.extension(file);
        long sizeBytes = attrs.size();

        FileMetadata metadata = buildMetadata(file, ext, sizeBytes, attrs);

        // Large file — metadata-only indexing
        if (isLargeFile(ext, sizeBytes, config)) {
            List<String> filenameTokens = Tokenizer.tokenizeFilename(
                    file.getFileName().toString());
            indexManager.indexDocument(metadata, filenameTokens);
            result.incrementMetadata();
            return;
        }

        // Full content indexing
        Optional<String> content = fileParser.parse(file);
        if (content.isEmpty()) {
            result.incrementSkipped();
            return;
        }

        List<String> tokens = Tokenizer.tokenize(content.get());

        // Also blend in filename tokens so filename search works
        List<String> filenameTokens = Tokenizer.tokenizeFilename(
                file.getFileName().toString());
        tokens.addAll(filenameTokens);

        indexManager.indexDocument(metadata, tokens);
        result.incrementIndexed();
    }

    private boolean isLargeFile(String ext, long sizeBytes, AppConfig config) {
        if (ext.equals(".pdf")) {
            return sizeBytes > config.getMaxPdfFileSizeBytes();
        }
        return sizeBytes > config.getMaxTextFileSizeBytes();
    }

    private FileMetadata buildMetadata(
            Path file, String ext, long sizeBytes, BasicFileAttributes attrs) {

        return new FileMetadata(
                0,                                          // docId assigned by DocumentStore
                file.toAbsolutePath().toString(),
                file.getFileName().toString(),
                ext,
                sizeBytes,
                attrs.lastModifiedTime().toMillis()
        );
    }
}