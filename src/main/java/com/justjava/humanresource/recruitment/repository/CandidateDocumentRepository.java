package com.justjava.humanresource.recruitment.repository;

import com.justjava.humanresource.recruitment.entity.CandidateDocument;
import com.justjava.humanresource.recruitment.enums.CandidateDocumentType;
import com.justjava.humanresource.recruitment.enums.DocumentExtractionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateDocumentRepository extends JpaRepository<CandidateDocument, Long> {
    List<CandidateDocument> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);
    List<CandidateDocument> findByApplicationIdAndDocumentTypeAndExtractedTextStatusOrderByCreatedAtAsc(
            Long applicationId, CandidateDocumentType documentType, DocumentExtractionStatus status);
}
