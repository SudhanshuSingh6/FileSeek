package com.fileseek.scanner;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;

public class PdfParser {

    private final PDFTextStripper stripper;

    public PdfParser() {

        stripper = new PDFTextStripper();

    }

    public String parse(Path file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            if (doc.isEncrypted()) {
                return "";
            }
            String text = stripper.getText(doc);
            return text != null ? text : "";
        }
    }
}