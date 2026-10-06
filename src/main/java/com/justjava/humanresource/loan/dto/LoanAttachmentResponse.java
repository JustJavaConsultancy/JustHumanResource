package com.justjava.humanresource.loan.dto;

import com.justjava.humanresource.loan.enums.LoanAttachmentType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** Attachment metadata (never the file bytes or the server storage path). */
@Value
@Builder
public class LoanAttachmentResponse {
    Long id;
    Long loanApplicationId;
    String originalFilename;
    String contentType;
    Long fileSize;
    LoanAttachmentType attachmentType;
    Long uploadedByEmployeeId;
    String uploadedByName;
    /** True when the current viewer may still delete it (owner, DRAFT/RETURNED only). */
    boolean deletable;
    LocalDateTime createdAt;
}
