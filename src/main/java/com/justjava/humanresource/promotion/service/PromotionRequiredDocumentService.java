package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.promotion.entity.PromotionConfiguration;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import com.justjava.humanresource.promotion.repository.PromotionAttachmentRepository;
import com.justjava.humanresource.promotion.repository.PromotionConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromotionRequiredDocumentService {

    public enum EnforcementStage {
        SUBMIT,
        IMPLEMENTATION
    }

    private final PromotionConfigurationRepository configurationRepository;
    private final PromotionAttachmentRepository attachmentRepository;

    @Transactional(readOnly = true)
    public void assertRequiredDocumentsPresent(PromotionRequest request, EnforcementStage stage) {
        List<PromotionAttachmentType> missing = missingRequiredDocuments(request, stage);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Promotion request is missing required documents: " + missing);
        }
    }

    @Transactional(readOnly = true)
    public List<PromotionAttachmentType> missingRequiredDocuments(PromotionRequest request, EnforcementStage stage) {
        if (request == null || request.getId() == null) {
            return List.of();
        }
        List<PromotionAttachmentType> required = requiredDocuments(stage);
        if (required.isEmpty()) {
            return List.of();
        }
        return required.stream()
                .distinct()
                .filter(type -> !attachmentRepository.existsByPromotionRequest_IdAndDocumentType(request.getId(), type))
                .toList();
    }

    private List<PromotionAttachmentType> requiredDocuments(EnforcementStage stage) {
        PromotionConfiguration configuration = configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc()
                .orElseGet(PromotionConfiguration::new);
        return switch (stage) {
            case SUBMIT -> configuration.getRequiredDocumentsBeforeSubmit() == null
                    ? List.of()
                    : configuration.getRequiredDocumentsBeforeSubmit();
            case IMPLEMENTATION -> configuration.getRequiredDocumentsBeforeImplementation() == null
                    ? List.of()
                    : configuration.getRequiredDocumentsBeforeImplementation();
        };
    }
}
