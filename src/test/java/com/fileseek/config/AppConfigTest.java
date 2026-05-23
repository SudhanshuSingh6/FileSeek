package com.fileseek.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    private AppConfig config;

    @BeforeEach
    void setUp() {
        config = new AppConfig();
    }

    // -------------------------------------------------------
    // watched directories
    // -------------------------------------------------------

    @Test
    void addWatchedDirectoryPersists() {
        config.addWatchedDirectory("/home/user/projects");
        assertTrue(config.getWatchedDirectories()
                .contains("/home/user/projects"));
    }

    @Test
    void addWatchedDirectoryNoDuplicates() {
        config.addWatchedDirectory("/home/user/projects");
        config.addWatchedDirectory("/home/user/projects");
        assertEquals(1, config.getWatchedDirectories().size());
    }

    @Test
    void addMultipleDistinctDirectories() {
        config.addWatchedDirectory("/home/user/projects");
        config.addWatchedDirectory("/home/user/documents");
        assertEquals(2, config.getWatchedDirectories().size());
    }

    @Test
    void removeWatchedDirectoryReturnsTrueWhenPresent() {
        config.addWatchedDirectory("/home/user/projects");
        assertTrue(config.removeWatchedDirectory("/home/user/projects"));
        assertFalse(config.getWatchedDirectories()
                .contains("/home/user/projects"));
    }

    @Test
    void removeWatchedDirectoryReturnsFalseWhenAbsent() {
        assertFalse(config.removeWatchedDirectory("/not/there"));
    }

    @Test
    void removeOneDirectoryLeavesOthersIntact() {
        config.addWatchedDirectory("/dir1");
        config.addWatchedDirectory("/dir2");
        config.removeWatchedDirectory("/dir1");
        assertTrue(config.getWatchedDirectories().contains("/dir2"));
        assertEquals(1, config.getWatchedDirectories().size());
    }

    @Test
    void initialWatchedDirectoriesIsEmpty() {
        assertTrue(config.getWatchedDirectories().isEmpty());
    }

    // -------------------------------------------------------
    // ignore rules
    // -------------------------------------------------------

    @Test
    void defaultIgnoredIncludesGit() {
        assertTrue(config.isIgnored(".git"));
    }

    @Test
    void defaultIgnoredIncludesNodeModules() {
        assertTrue(config.isIgnored("node_modules"));
    }

    @Test
    void defaultIgnoredIncludesTarget() {
        assertTrue(config.isIgnored("target"));
    }

    @Test
    void defaultIgnoredIncludesBuild() {
        assertTrue(config.isIgnored("build"));
    }

    @Test
    void defaultIgnoredIncludesDist() {
        assertTrue(config.isIgnored("dist"));
    }

    @Test
    void defaultIgnoredIncludesIdea() {
        assertTrue(config.isIgnored(".idea"));
    }

    @Test
    void nonIgnoredDirectoryReturnsFalse() {
        assertFalse(config.isIgnored("myproject"));
        assertFalse(config.isIgnored("src"));
        assertFalse(config.isIgnored("docs"));
    }

    // -------------------------------------------------------
    // extension support
    // -------------------------------------------------------

    @Test
    void defaultSupportedExtensionsIncludeTxt() {
        assertTrue(config.isSupportedExtension(".txt"));
    }

    @Test
    void defaultSupportedExtensionsIncludeMd() {
        assertTrue(config.isSupportedExtension(".md"));
    }

    @Test
    void defaultSupportedExtensionsIncludeJava() {
        assertTrue(config.isSupportedExtension(".java"));
    }

    @Test
    void defaultSupportedExtensionsIncludeJson() {
        assertTrue(config.isSupportedExtension(".json"));
    }

    @Test
    void defaultSupportedExtensionsIncludeXml() {
        assertTrue(config.isSupportedExtension(".xml"));
    }

    @Test
    void defaultSupportedExtensionsIncludeYml() {
        assertTrue(config.isSupportedExtension(".yml"));
    }

    @Test
    void defaultSupportedExtensionsIncludeProperties() {
        assertTrue(config.isSupportedExtension(".properties"));
    }

    @Test
    void defaultSupportedExtensionsIncludePdf() {
        assertTrue(config.isSupportedExtension(".pdf"));
    }

    @Test
    void extensionCheckIsCaseInsensitive() {
        assertTrue(config.isSupportedExtension(".TXT"));
        assertTrue(config.isSupportedExtension(".Md"));
        assertTrue(config.isSupportedExtension(".JAVA"));
        assertTrue(config.isSupportedExtension(".PDF"));
    }

    @Test
    void unsupportedExtensionsReturnFalse() {
        assertFalse(config.isSupportedExtension(".exe"));
        assertFalse(config.isSupportedExtension(".png"));
        assertFalse(config.isSupportedExtension(".mp4"));
        assertFalse(config.isSupportedExtension(".zip"));
        assertFalse(config.isSupportedExtension(".class"));
    }

    // -------------------------------------------------------
    // size thresholds
    // -------------------------------------------------------

    @Test
    void defaultTextSizeThresholdIs15MB() {
        assertEquals(15L * 1024 * 1024, config.getMaxTextFileSizeBytes());
    }

    @Test
    void defaultPdfSizeThresholdIs5MB() {
        assertEquals(5L * 1024 * 1024, config.getMaxPdfFileSizeBytes());
    }

    @Test
    void textSizeThresholdIsConfigurable() {
        config.setMaxTextFileSizeBytes(1024);
        assertEquals(1024, config.getMaxTextFileSizeBytes());
    }

    @Test
    void pdfSizeThresholdIsConfigurable() {
        config.setMaxPdfFileSizeBytes(512);
        assertEquals(512, config.getMaxPdfFileSizeBytes());
    }

    @Test
    void settingLowThresholdDoesNotAffectExtensionSupport() {
        config.setMaxTextFileSizeBytes(1);   // 1 byte threshold
        // Extension support is independent of size threshold
        assertTrue(config.isSupportedExtension(".txt"));
    }
}
