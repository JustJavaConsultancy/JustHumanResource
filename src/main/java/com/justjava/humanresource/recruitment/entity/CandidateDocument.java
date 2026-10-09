package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.recruitment.enums.CandidateDocumentType;
import com.justjava.humanresource.recruitment.enums.DocumentExtractionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "recruitment_candidate_documents", indexes = {
        @Index(name = "idx_candidate_doc_application", columnList = "applicationId"),
        @Index(name = "idx_candidate_doc_candidate", columnList = "candidateId")
})
public class CandidateDocument extends BaseEntity {

    @Column(nullable = false)
    private Long candidateId;

    @Column(nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CandidateDocumentType documentType = CandidateDocumentType.RESUME;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false, length = 1000)
    private String storagePath;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @Column(nullable = false)
    private boolean uploadedByCandidate;

    private Long uploadedByEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentExtractionStatus extractedTextStatus = DocumentExtractionStatus.PENDING;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(length = 2000)
    private String extractionError;
}
