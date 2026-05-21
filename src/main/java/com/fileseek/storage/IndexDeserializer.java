package com.fileseek.storage;

import com.fileseek.index.DocumentStore;
import com.fileseek.index.InvertedIndex;
import com.fileseek.model.FileMetadata;
import com.fileseek.model.Posting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class IndexDeserializer {

    private final Path indexFile;

    public IndexDeserializer(Path indexFile) {
        this.indexFile = indexFile;
    }

    public void deserialize(DocumentStore docStore, InvertedIndex invertedIndex)
            throws IOException {

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(
                        new GZIPInputStream(
                                new FileInputStream(indexFile.toFile()))))) {

            readHeader(in);
            readDocumentStore(in, docStore);
            readInvertedIndex(in, invertedIndex);
        }
    }

    private void readHeader(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != IndexSerializer.MAGIC) {
            throw new IOException(
                    String.format("Invalid index file — bad magic: 0x%X", magic));
        }

        int version = in.readInt();
        if (version != IndexSerializer.VERSION) {
            throw new IOException(
                    String.format("Unsupported index version: %d (expected %d)",
                            version, IndexSerializer.VERSION));
        }
    }

    private void readDocumentStore(DataInputStream in, DocumentStore store)
            throws IOException {
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            int docId = in.readInt();
            String path = readString(in);
            String fileName = readString(in);
            String extension = readString(in);
            long sizeBytes = in.readLong();
            long lastModified = in.readLong();
            long indexedAt = in.readLong();
            int tokenCount = in.readInt();

            FileMetadata meta = new FileMetadata(
                    docId, path, fileName, extension, sizeBytes, lastModified);
            meta.setIndexedAt(indexedAt);
            meta.setTokenCount(tokenCount);
            store.restoreDocument(meta);
        }
    }

    private void readInvertedIndex(DataInputStream in, InvertedIndex index)
            throws IOException {

        int termCount = in.readInt();
        for (int i = 0; i < termCount; i++) {
            String term = readString(in);
            int postingCount = in.readInt();

            List<Posting> postings = new ArrayList<>(postingCount);
            for (int j = 0; j < postingCount; j++) {
                int docId = in.readInt();
                List<Integer> positions = readPositions(in);
                postings.add(new Posting(docId, positions));
            }

            index.restorePostings(term, postings);
        }
    }

    private List<Integer> readPositions(DataInputStream in) throws IOException {
        int count = in.readInt();
        List<Integer> positions = new ArrayList<>(count);
        int current = 0;
        for (int i = 0; i < count; i++) {
            current += in.readInt();
            positions.add(current);
        }
        return positions;
    }


    private String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}