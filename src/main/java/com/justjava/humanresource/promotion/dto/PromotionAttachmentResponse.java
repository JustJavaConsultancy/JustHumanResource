package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.enums.PromotionAttachmentType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class PromotionAttachmentResponse {
    Long id;
    PromotionAttachmentType documentType;
    String fileName;
    String contentType;
    long fileSize;
    String generatedTemplateKey;
    String generatedTemplateName;
    String generatedTemplateVersion;
    Long documentLibraryDocumentId;
    LocalDateTime documentLibrarySyncedAt;
    String documentLibrarySyncError;
    String uploadedByName;
    LocalDateTime uploadedAt;
    boolean required;
}
