package com.fileseek.storage;

import com.fileseek.index.DocumentStore;
import com.fileseek.index.InvertedIndex;
import com.fileseek.model.FileMetadata;
import com.fileseek.model.Posting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.GZIPOutputStream;

public class IndexSerializer {

    static final int MAGIC = 0x46534558;
    static final int VERSION = 2;

    private final Path indexFile;

    public IndexSerializer(Path indexFile) {
        this.indexFile = indexFile;
    }

    public void serialize(DocumentStore docStore, InvertedIndex invertedIndex)
            throws IOException {

        Files.createDirectories(indexFile.getParent());

        Path tempFile = indexFile.resolveSibling(indexFile.getFileName() + ".tmp");

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(
                        new GZIPOutputStream(
                                new FileOutputStream(tempFile.toFile()))))) {

            writeHeader(out);
            writeDocumentStore(out, docStore);
            writeInvertedIndex(out, invertedIndex);
        }

        Files.move(tempFile, indexFile, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }


    private void writeHeader(DataOutputStream out) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
    }

    private void writeDocumentStore(DataOutputStream out, DocumentStore store)
            throws IOException {
        var docs = store.getAllDocuments();
        out.writeInt(docs.size());
        for (FileMetadata meta : docs) {
            out.writeInt(meta.getDocId());
            writeString(out, meta.getPath());
            writeString(out, meta.getFileName());
            writeString(out, meta.getExtension());
            out.writeLong(meta.getSizeBytes());
            out.writeLong(meta.getLastModified());
            out.writeLong(meta.getIndexedAt());
            out.writeInt(meta.getTokenCount());
        }
    }

    private void writeInvertedIndex(DataOutputStream out, InvertedIndex index)
            throws IOException {

        var terms = index.getAllTerms();
        out.writeInt(terms.size());

        for (String term : terms) {
            writeString(out, term);

            List<Posting> postings = index.getPostings(term);
            out.writeInt(postings.size());

            for (Posting posting : postings) {
                out.writeInt(posting.docId());
                writePositions(out, posting.positions());
            }
        }
    }

    private void writePositions(DataOutputStream out, List<Integer> positions)
            throws IOException {

        out.writeInt(positions.size());
        int previous = 0;
        for (int position : positions) {
            out.writeInt(position - previous);
            previous = position;
        }
    }

    private void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

}