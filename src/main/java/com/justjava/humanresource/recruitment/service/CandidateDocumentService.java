package com.justjava.humanresource.recruitment.service;

import com.justjava.humanresource.recruitment.entity.Candidate;
import com.justjava.humanresource.recruitment.entity.CandidateDocument;
import com.justjava.humanresource.recruitment.entity.JobApplication;
import com.justjava.humanresource.recruitment.enums.CandidateDocumentType;
import com.justjava.humanresource.recruitment.repository.CandidateDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateDocumentService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final CandidateDocumentRepository documentRepository;
    private final CandidateDocumentExtractionService extractionService;

    @Value("${app.recruitment.documents.storage-path:${user.home}/just-hr/recruitment-candidate-documents}")
    private String storageRoot;

    @Transactional
    public CandidateDocument storeCandidateUpload(JobApplication application,
                                                  Candidate candidate,
                                                  MultipartFile file,
                                                  CandidateDocumentType documentType) {
        CandidateDocument document = store(application, candidate.getId(), file, documentType, true, null);
        return extractionService.extract(document);
    }

    @Transactional
    public CandidateDocument storeRecruiterUpload(JobApplication application,
                                                  MultipartFile file,
                                                  CandidateDocumentType documentType,
                                                  Long employeeId) {
        CandidateDocument document = store(application, application.getCandidateId(), file, documentType, false, employeeId);
        return extractionService.extract(document);
    }

    @Transactional(readOnly = true)
    public List<CandidateDocument> findByApplication(Long applicationId) {
        return documentRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId);
    }

    @Transactional(readOnly = true)
    public CandidateDocument requireDocumentForApplication(Long applicationId, Long documentId) {
        CandidateDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate document not found."));
        if (!document.getApplicationId().equals(applicationId)) {
            throw new IllegalArgumentException("Candidate document does not belong to this application.");
        }
        return document;
    }

    @Transactional(readOnly = true)
    public Resource load(CandidateDocument document) {
        Resource resource = new FileSystemResource(Paths.get(document.getStoragePath()).normalize());
        if (!resource.exists()) {
            throw new IllegalStateException("Candidate document file is missing.");
        }
        return resource;
    }

    public void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty candidate document is required.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Candidate document exceeds the 20 MB limit.");
        }
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported candidate document type: " + contentType);
        }
    }

    private CandidateDocument store(JobApplication application,
                                    Long candidateId,
                                    MultipartFile file,
                                    CandidateDocumentType documentType,
                                    boolean uploadedByCandidate,
                                    Long uploadedByEmployeeId) {
        validateUpload(file);
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        String originalFilename = Paths.get(Optional.ofNullable(file.getOriginalFilename()).orElse("candidate-document"))
                .getFileName()
                .toString();
        String storedFilename = UUID.randomUUID() + extension(originalFilename);

        try {
            Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
            Path directory = root.resolve(String.valueOf(application.getId())).normalize();
            if (!directory.startsWith(root)) {
                throw new IllegalStateException("Invalid candidate document storage path.");
            }
            Files.createDirectories(directory);
            Path target = directory.resolve(storedFilename).normalize();
            if (!target.startsWith(directory)) {
                throw new IllegalStateException("Invalid candidate document filename.");
            }
            try (var input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }

            CandidateDocument document = new CandidateDocument();
            document.setCandidateId(candidateId);
            document.setApplicationId(application.getId());
            document.setDocumentType(documentType == null ? CandidateDocumentType.OTHER : documentType);
            document.setOriginalFilename(originalFilename);
            document.setContentType(contentType);
            document.setFileSize(file.getSize());
            document.setStoragePath(target.toString());
            document.setUploadedAt(LocalDateTime.now());
            document.setUploadedByCandidate(uploadedByCandidate);
            document.setUploadedByEmployeeId(uploadedByEmployeeId);
            return documentRepository.save(document);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store candidate document.", ex);
        }
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index);
    }
}
