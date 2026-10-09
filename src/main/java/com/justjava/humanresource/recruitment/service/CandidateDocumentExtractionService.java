package com.justjava.humanresource.recruitment.service;

import com.justjava.humanresource.recruitment.entity.CandidateDocument;
import com.justjava.humanresource.recruitment.enums.DocumentExtractionStatus;
import com.justjava.humanresource.recruitment.repository.CandidateDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CandidateDocumentExtractionService {

    private static final int MAX_EXTRACTED_TEXT_LENGTH = 80_000;

    private final CandidateDocumentRepository documentRepository;

    @Transactional
    public CandidateDocument extract(CandidateDocument document) {
        try {
            String text = extractText(document);
            if (text == null || text.isBlank()) {
                document.setExtractedText(null);
                document.setExtractedTextStatus(DocumentExtractionStatus.UNREADABLE);
                document.setExtractionError("No readable text was found in this document.");
            } else {
                document.setExtractedText(truncate(text.trim(), MAX_EXTRACTED_TEXT_LENGTH));
                document.setExtractedTextStatus(DocumentExtractionStatus.EXTRACTED);
                document.setExtractionError(null);
            }
        } catch (Exception ex) {
            document.setExtractedText(null);
            document.setExtractedTextStatus(DocumentExtractionStatus.FAILED);
            document.setExtractionError(truncate(ex.getMessage(), 2000));
        }
        return documentRepository.save(document);
    }

    private String extractText(CandidateDocument document) throws IOException {
        Path path = Paths.get(document.getStoragePath()).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IOException("Stored candidate document file is missing.");
        }
        String filename = document.getOriginalFilename() == null ? "" : document.getOriginalFilename().toLowerCase(Locale.ROOT);
        String contentType = document.getContentType() == null ? "" : document.getContentType().toLowerCase(Locale.ROOT);

        if (contentType.equals("application/pdf") || filename.endsWith(".pdf")) {
            try (PDDocument pdf = PDDocument.load(path.toFile())) {
                return new PDFTextStripper().getText(pdf);
            }
        }
        if (contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                || filename.endsWith(".docx")) {
            try (XWPFDocument docx = new XWPFDocument(Files.newInputStream(path))) {
                return docx.getParagraphs().stream()
                        .map(XWPFParagraph::getText)
                        .filter(text -> text != null && !text.isBlank())
                        .reduce("", (left, right) -> left + System.lineSeparator() + right);
            }
        }
        if (contentType.startsWith("text/") || filename.endsWith(".txt")) {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        if (filename.endsWith(".doc") || contentType.equals("application/msword")) {
            throw new IOException("Legacy DOC files are stored but text extraction is not supported yet. Upload PDF, DOCX, or TXT for AI shortlisting.");
        }
        throw new IOException("Unsupported document type for text extraction.");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
