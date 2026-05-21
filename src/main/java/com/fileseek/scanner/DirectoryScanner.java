package com.fileseek.scanner;

import com.fileseek.config.AppConfig;
import com.fileseek.index.IndexManager;
import com.fileseek.model.FileMetadata;
import com.fileseek.util.PathUtils;
import com.fileseek.util.Tokenizer;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class DirectoryScanner {

    private final FileParser fileParser = new FileParser();

    public ScanResult scan(
            Path root,
            AppConfig config,
            IndexManager indexManager,
            BiConsumer<Integer, String> onProgress) {

        ScanResult result = new ScanResult();
        long startMs = System.currentTimeMillis();

        int removed = removeDeletedDocuments(root, indexManager);
        result.setFilesRemoved(removed);

        try {
            Files.walkFileTree(root, Set.of(), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {

                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path dir, BasicFileAttributes attrs) {
                            String name = dir.getFileName().toString();
                            if (config.isIgnored(name) || name.startsWith(".")) {
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
                    });
        } catch (IOException e) {
            result.addError("Root scan failed: " + e.getMessage());
        }

        result.setDurationMs(System.currentTimeMillis() - startMs);
        return result;
    }

    public int removeDeletedDocuments(Path rootDir, IndexManager indexManager) {
        Path normalizedRoot = rootDir.toAbsolutePath().normalize();

        List<String> toRemove = indexManager.getDocumentStore()
                .getAllDocuments()
                .stream()
                .filter(meta -> {
                    try {
                        // PathUtils.isUnder uses Path.startsWith — correct on all platforms
                        return PathUtils.isUnder(
                                Path.of(meta.getPath()), normalizedRoot);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .filter(meta -> !Files.exists(Path.of(meta.getPath())))
                .map(FileMetadata::getPath)
                .collect(Collectors.toList());

        toRemove.forEach(indexManager::removeDocument);
        return toRemove.size();
    }

    private void processFile(
            Path file,
            BasicFileAttributes attrs,
            AppConfig config,
            IndexManager indexManager,
            ScanResult result) {

        if (!fileParser.isSupported(file)) {
            result.incrementSkipped();
            return;
        }

        String absolutePath = file.toAbsolutePath().toString();

        if (indexManager.isIndexed(absolutePath)) {
            long fileLastModified = attrs.lastModifiedTime().toMillis();
            long indexedLastModified = indexManager.getDocumentStore()
                    .getByPath(absolutePath)
                    .map(FileMetadata::getLastModified)
                    .orElse(0L);

            if (fileLastModified <= indexedLastModified) {
                result.incrementSkipped();
                return;
            }

            indexManager.removeDocument(absolutePath);
            result.incrementUpdated();
        }

        String ext = FileParser.extension(file);
        long sizeBytes = attrs.size();

        FileMetadata metadata = new FileMetadata(
                0, absolutePath, file.getFileName().toString(),
                ext, sizeBytes, attrs.lastModifiedTime().toMillis());

        if (isLargeFile(ext, sizeBytes, config)) {
            List<String> filenameTokens =
                    Tokenizer.tokenizeFilename(file.getFileName().toString());
            indexManager.indexDocument(metadata, filenameTokens);
            result.incrementMetadata();
            return;
        }

        var content = fileParser.parse(file);
        if (content.isEmpty()) {
            result.incrementSkipped();
            return;
        }

        List<String> tokens = Tokenizer.tokenize(content.get());
        tokens.addAll(Tokenizer.tokenizeFilename(file.getFileName().toString()));

        indexManager.indexDocument(metadata, tokens);
        result.incrementIndexed();
    }

    private boolean isLargeFile(String ext, long sizeBytes, AppConfig config) {
        return ext.equals(".pdf")
                ? sizeBytes > config.getMaxPdfFileSizeBytes()
                : sizeBytes > config.getMaxTextFileSizeBytes();
    }

    public int countIndexableFiles(Path root, AppConfig config) {
        int[] count = {0};
        try {
            Files.walkFileTree(root, Set.of(), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(
                                Path dir, BasicFileAttributes attrs) {
                            String name = dir.getFileName().toString();
                            if (config.isIgnored(name) || name.startsWith(".")) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(
                                Path file, BasicFileAttributes attrs) {
                            if (fileParser.isSupported(file)) count[0]++;
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(
                                Path file, IOException exc) {
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
        }
        return count[0];
    }
}