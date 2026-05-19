package com.fileseek.storage;

import java.io.*;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

public class CorruptionChecker {

    public static boolean isCorrupted(Path indexFile) {
        if (!indexFile.toFile().exists()) return false;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(
                        new GZIPInputStream(
                                new FileInputStream(indexFile.toFile()))))) {

            int magic = in.readInt();
            int version = in.readInt();

            if (magic != IndexSerializer.MAGIC) {
                System.err.printf(
                        "[warn] Index corrupted — bad magic number: 0x%X%n", magic);
                return true;
            }

            if (version != IndexSerializer.VERSION) {
                System.err.printf(
                        "[warn] Index version mismatch: found %d, expected %d%n",
                        version, IndexSerializer.VERSION);
                return true;
            }

            return false;

        } catch (IOException e) {
            System.err.println("[warn] Index file unreadable: " + e.getMessage());
            return true;
        }
    }

    public static void deleteCorrupted(Path indexFile) {
        try {
            if (indexFile.toFile().exists()) {
                indexFile.toFile().delete();
                System.out.println("[info] Corrupted index deleted. " +
                        "Run 'fileseek add <dir>' to rebuild.");
            }
        } catch (Exception e) {
            System.err.println("[error] Could not delete corrupted index: "
                    + e.getMessage());
        }
    }
}