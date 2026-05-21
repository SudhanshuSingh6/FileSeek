package com.fileseek.util;

import java.nio.file.Path;

public final class PathUtils {

    private PathUtils() {
    }

    public static Path expand(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        String expanded = rawPath.startsWith("~")
                ? System.getProperty("user.home") + rawPath.substring(1)
                : rawPath;
        return Path.of(expanded).toAbsolutePath().normalize();
    }

    public static boolean isUnder(Path file, Path root) {
        return file.normalize().startsWith(root.normalize());
    }

    public static String parentOf(String pathStr) {
        Path parent = Path.of(pathStr).getParent();
        return (parent != null) ? parent.toString() : pathStr;
    }
}