package com.justjava.humanresource.promotion;

import com.justjava.humanresource.promotion.entity.PromotionConfiguration;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import com.justjava.humanresource.promotion.repository.PromotionAttachmentRepository;
import com.justjava.humanresource.promotion.repository.PromotionConfigurationRepository;
import com.justjava.humanresource.promotion.service.PromotionRequiredDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionRequiredDocumentServiceTest {

    @Mock PromotionConfigurationRepository configurationRepository;
    @Mock PromotionAttachmentRepository attachmentRepository;

    private PromotionRequiredDocumentService service;

    @BeforeEach
    void setUp() {
        service = new PromotionRequiredDocumentService(configurationRepository, attachmentRepository);
    }

    @Test
    void returnsMissingRequiredSubmitDocuments() {
        PromotionRequest request = request();
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setRequiredDocumentsBeforeSubmit(List.of(
                PromotionAttachmentType.APPRAISAL_SUMMARY,
                PromotionAttachmentType.MANAGER_RECOMMENDATION
        ));
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));
        when(attachmentRepository.existsByPromotionRequest_IdAndDocumentType(
                10L, PromotionAttachmentType.APPRAISAL_SUMMARY)).thenReturn(true);
        when(attachmentRepository.existsByPromotionRequest_IdAndDocumentType(
                10L, PromotionAttachmentType.MANAGER_RECOMMENDATION)).thenReturn(false);

        List<PromotionAttachmentType> missing = service.missingRequiredDocuments(
                request, PromotionRequiredDocumentService.EnforcementStage.SUBMIT);

        assertThat(missing).containsExactly(PromotionAttachmentType.MANAGER_RECOMMENDATION);
    }

    @Test
    void assertionFailsWhenImplementationDocumentsAreMissing() {
        PromotionRequest request = request();
        PromotionConfiguration configuration = new PromotionConfiguration();
        configuration.setRequiredDocumentsBeforeImplementation(List.of(PromotionAttachmentType.PROMOTION_LETTER));
        when(configurationRepository.findFirstByActiveTrueOrderByUpdatedAtDescIdDesc())
                .thenReturn(Optional.of(configuration));

        assertThatThrownBy(() -> service.assertRequiredDocumentsPresent(
                request, PromotionRequiredDocumentService.EnforcementStage.IMPLEMENTATION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROMOTION_LETTER");
    }

    @Test
    void ignoresUnsavedRequests() {
        PromotionRequest request = new PromotionRequest();

        List<PromotionAttachmentType> missing = service.missingRequiredDocuments(
                request, PromotionRequiredDocumentService.EnforcementStage.SUBMIT);

        assertThat(missing).isEmpty();
    }

    private PromotionRequest request() {
        PromotionRequest request = new PromotionRequest();
        request.setId(10L);
        return request;
    }
}
