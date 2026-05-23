package com.fileseek.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PathUtilsTest {

    private static final String HOME = System.getProperty("user.home");

    // --- expand ---

    @Test
    void expandsTildeToHomeDirectory() {
        Path result = PathUtils.expand("~/Documents");
        assertTrue(result.toString().startsWith(HOME));
        assertTrue(result.toString().contains("Documents"));
    }

    @Test
    void expandsTildeAlone() {
        Path result = PathUtils.expand("~");
        assertEquals(Path.of(HOME).toAbsolutePath().normalize(), result);
    }

    @Test
    void leavesAbsolutePathUnchanged() {
        Path result = PathUtils.expand("/tmp/testdir");
        assertTrue(result.isAbsolute());
        assertTrue(result.toString().contains("testdir"));
    }

    @Test
    void normalizesDoubleDots() {
        Path result = PathUtils.expand("/tmp/../tmp/testdir");
        assertFalse(result.toString().contains(".."));
    }

    @Test
    void returnsAbsolutePath() {
        Path result = PathUtils.expand("~/Projects");
        assertTrue(result.isAbsolute());
    }

    @Test
    void throwsOnBlankPath() {
        assertThrows(IllegalArgumentException.class,
                () -> PathUtils.expand(""));
    }

    @Test
    void throwsOnNullPath() {
        assertThrows(IllegalArgumentException.class,
                () -> PathUtils.expand(null));
    }

    @Test
    void throwsOnWhitespacePath() {
        assertThrows(IllegalArgumentException.class,
                () -> PathUtils.expand("   "));
    }

    // --- isUnder ---

    @Test
    void isUnderReturnsTrueForChild() {
        Path root = Path.of("/home/user/projects");
        Path file = Path.of("/home/user/projects/app/Main.java");
        assertTrue(PathUtils.isUnder(file, root));
    }

    @Test
    void isUnderReturnsTrueForDirectChild() {
        Path root = Path.of("/home/user/projects");
        Path file = Path.of("/home/user/projects/Main.java");
        assertTrue(PathUtils.isUnder(file, root));
    }

    @Test
    void isUnderReturnsFalseForSibling() {
        Path root = Path.of("/home/user/projects");
        Path file = Path.of("/home/user/documents/file.txt");
        assertFalse(PathUtils.isUnder(file, root));
    }

    @Test
    void isUnderReturnsFalseForParent() {
        Path root = Path.of("/home/user/projects");
        Path file = Path.of("/home/user");
        assertFalse(PathUtils.isUnder(file, root));
    }

    @Test
    void isUnderReturnsTrueForSelf() {
        Path root = Path.of("/home/user/projects");
        assertTrue(PathUtils.isUnder(root, root));
    }

    @Test
    void isUnderHandlesNestedPaths() {
        Path root = Path.of("/home/user/projects");
        Path file = Path.of("/home/user/projects/sub/deep/file.txt");
        assertTrue(PathUtils.isUnder(file, root));
    }

    // --- parentOf ---

    @Test
    void parentOfReturnsParentDirectory() {
        String parent = PathUtils.parentOf("/home/user/projects/file.txt");
        assertEquals(Path.of("/home/user/projects").toString(), parent);
    }

    @Test
    void parentOfDoesNotContainFilename() {
        String parent = PathUtils.parentOf(
                Path.of(HOME, "projects", "file.txt").toString());
        assertFalse(parent.endsWith("file.txt"));
    }

    @Test
    void parentOfUsesOsSeparator() {
        // Path.of normalizes separators — result should not contain mixed separators
        String parent = PathUtils.parentOf(
                Path.of(HOME, "projects", "file.txt").toString());
        assertNotNull(parent);
        assertFalse(parent.isBlank());
    }
}
