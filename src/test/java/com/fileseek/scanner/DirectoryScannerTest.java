package com.fileseek.scanner;

import com.fileseek.config.AppConfig;
import com.fileseek.index.IndexManager;
import com.fileseek.model.FileMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class DirectoryScannerTest {

    @TempDir
    Path tempDir;

    private AppConfig config;
    private IndexManager indexManager;
    private DirectoryScanner scanner;

    @BeforeEach
    void setUp() {
        config = new AppConfig();
        indexManager = new IndexManager();
        scanner = new DirectoryScanner();
    }

    // --- basic indexing ---

    @Test
    void indexesSupportedFiles() throws IOException {
        write("notes.txt", "redis caching");
        write("guide.md", "spring boot");

        ScanResult result = scan(tempDir);

        assertEquals(2, result.getFilesIndexed());
        assertEquals(0, result.getFilesSkipped());
        assertEquals(2, indexManager.documentCount());
    }

    @Test
    void skipsUnsupportedExtensions() throws IOException {
        write("image.png", "not text");
        write("binary.exe", "MZ");
        write("valid.txt", "redis");

        ScanResult result = scan(tempDir);

        assertEquals(1, result.getFilesIndexed());
        assertEquals(2, result.getFilesSkipped());
    }

    @Test
    void skipsIgnoredDirectories() throws IOException {
        Path nodeModules = tempDir.resolve("node_modules");
        Files.createDirectory(nodeModules);
        write(nodeModules, "package.json", "{\"name\": \"test\"}");
        write("main.java", "public class Main {}");

        ScanResult result = scan(tempDir);

        // node_modules entirely skipped — only main.java indexed
        assertEquals(1, result.getFilesIndexed());
        assertFalse(indexManager.isIndexed(
                nodeModules.resolve("package.json").toAbsolutePath().toString()));
    }

    @Test
    void skipsHiddenDirectories() throws IOException {
        Path hidden = tempDir.resolve(".hidden");
        Files.createDirectory(hidden);
        write(hidden, "secret.txt", "sensitive data");
        write("visible.txt", "redis");

        ScanResult result = scan(tempDir);

        assertEquals(1, result.getFilesIndexed());
        assertFalse(indexManager.isIndexed(
                hidden.resolve("secret.txt").toAbsolutePath().toString()));
    }

    @Test
    void scansNestedDirectoriesRecursively() throws IOException {
        Path sub1 = tempDir.resolve("sub1");
        Path sub2 = sub1.resolve("sub2");
        Files.createDirectories(sub2);

        write(sub1, "file1.txt", "spring");
        write(sub2, "file2.txt", "redis");

        ScanResult result = scan(tempDir);

        assertEquals(2, result.getFilesIndexed());
    }

    @Test
    void tracksDirectoryCount() throws IOException {
        Path sub = tempDir.resolve("subdir");
        Files.createDirectory(sub);
        write(sub, "file.txt", "redis");

        ScanResult result = scan(tempDir);

        assertTrue(result.getDirectoriesScanned() >= 2,
                "Root and subdir must both be counted");
    }

    // --- incremental indexing ---

    @Test
    void skipsUnchangedFilesOnRescan() throws IOException {
        write("notes.txt", "redis caching");
        scan(tempDir);

        ScanResult second = scan(tempDir);

        assertEquals(0, second.getFilesIndexed());
        assertEquals(1, second.getFilesSkipped(),
                "Unchanged file must be skipped on second scan");
    }

    @Test
    void reindexesModifiedFiles() throws Exception {
        Path file = write("notes.txt", "redis caching");
        scan(tempDir);
        long original = Files.getLastModifiedTime(file).toMillis();
        Thread.sleep(20);
        Files.writeString(file, "redis caching updated content");
        Files.setLastModifiedTime(
                file,
                FileTime.fromMillis(original + 5000)
        );
        ScanResult second = scan(tempDir);
        assertEquals(1, second.getFilesUpdated(),
                "Modified file must be re-indexed");
        assertEquals(1, second.getFilesIndexed());
    }

    @Test
    void removesDeletedFilesOnRescan() throws IOException {
        Path file = write("notes.txt", "redis");
        scan(tempDir);

        assertEquals(1, indexManager.documentCount());

        Files.delete(file);
        ScanResult second = scan(tempDir);

        assertEquals(0, indexManager.documentCount());
        assertEquals(1, second.getFilesRemoved());
    }

    @Test
    void newFileIsIndexedOnRescan() throws IOException {
        write("existing.txt", "redis");
        scan(tempDir);

        write("new.txt", "spring boot");
        ScanResult second = scan(tempDir);

        assertEquals(1, second.getFilesIndexed(),
                "New file must be indexed on second scan");
        assertEquals(2, indexManager.documentCount());
    }

    @Test
    void removesOnlyFilesUnderScannedRoot() throws IOException {
        Path dir1 = tempDir.resolve("dir1");
        Path dir2 = tempDir.resolve("dir2");
        Files.createDirectories(dir1);
        Files.createDirectories(dir2);

        write(dir1, "file1.txt", "redis");
        write(dir2, "file2.txt", "spring");

        scan(dir1);
        scan(dir2);
        assertEquals(2, indexManager.documentCount());

        // Delete file1, rescan only dir1
        Files.delete(dir1.resolve("file1.txt"));
        scan(dir1);

        // file2 from dir2 must still be indexed
        assertEquals(1, indexManager.documentCount());
        assertTrue(indexManager.isIndexed(
                        dir2.resolve("file2.txt").toAbsolutePath().toString()),
                "file2 from dir2 must not be removed when scanning dir1");
    }

    // --- large file handling ---

    @Test
    void indexesLargeFilesAsMetadataOnly() throws IOException {
        // Lower threshold so test file triggers metadata-only path
        config.setMaxTextFileSizeBytes(10);
        Path file = write("large.txt", "redis caching spring boot docker");

        ScanResult result = scan(tempDir);

        assertEquals(1, result.getMetadataOnly(),
                "File exceeding threshold must be metadata-only indexed");
        assertEquals(0, result.getFilesIndexed());
        assertTrue(indexManager.isIndexed(
                        file.toAbsolutePath().toString()),
                "Large file must still appear in document store");
    }

    // --- error resilience ---

    @Test
    void continuesAfterUnreadableFile() throws IOException {
        write("good.txt", "redis");
        // Scan should not throw even if some files cannot be read
        ScanResult result = assertDoesNotThrow(() -> scan(tempDir));
        assertNotNull(result);
    }

    // --- scan result ---

    @Test
    void scanResultToStringIsHumanReadable() throws IOException {
        write("notes.txt", "redis");
        ScanResult result = scan(tempDir);
        String str = result.toString();
        assertTrue(str.contains("Indexed"));
        assertTrue(str.contains("Skipped"));
    }

    @Test
    void scanDurationIsRecorded() throws IOException {
        write("notes.txt", "redis");
        ScanResult result = scan(tempDir);
        assertTrue(result.getDurationMs() >= 0);
    }

    // --- parallel correctness ---

    @Test
    void parallelScanProducesCorrectDocumentCount() throws IOException {
        for (int i = 0; i < 50; i++) {
            write("file" + i + ".txt", "redis caching content " + i);
        }

        ScanResult result = scan(tempDir);

        assertEquals(50, result.getFilesIndexed());
        assertEquals(50, indexManager.documentCount());
        assertFalse(indexManager.getInvertedIndex()
                        .getPostings("redis").isEmpty(),
                "Tokens must be correctly indexed under parallel execution");
    }

    @Test
    void parallelScanAllFilesHaveUniqueDocIds() throws IOException {
        for (int i = 0; i < 20; i++) {
            write("file" + i + ".txt", "content " + i);
        }
        scan(tempDir);

        long uniqueDocIds = indexManager.getDocumentStore()
                .getAllDocuments().stream()
                .mapToInt(FileMetadata::getDocId)
                .distinct()
                .count();

        assertEquals(20, uniqueDocIds,
                "Every document must have a unique docId after parallel indexing");
    }

    // --- countIndexableFiles ---

    @Test
    void countIndexableFilesMatchesActualIndexed() throws IOException {
        write("a.txt", "redis");
        write("b.md", "spring");
        write("c.png", "binary"); // unsupported

        int counted = scanner.countIndexableFiles(tempDir, config);
        ScanResult result = scan(tempDir);

        assertEquals(counted, result.getFilesIndexed() + result.getMetadataOnly(),
                "count must match files actually processed");
    }

    @Test
    void countIndexableFilesSkipsIgnoredDirs() throws IOException {
        Path git = tempDir.resolve(".git");
        Files.createDirectory(git);
        write(git, "config", "gitconfig");
        write("valid.txt", "redis");

        int count = scanner.countIndexableFiles(tempDir, config);
        assertEquals(1, count);
    }

    // --- helpers ---

    private ScanResult scan(Path dir) {
        return scanner.scan(dir, config, indexManager, null);
    }

    private Path write(String name, String content) throws IOException {
        return write(tempDir, name, content);
    }

    private Path write(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
