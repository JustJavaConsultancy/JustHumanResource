package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.loan.dto.LoanAttachmentResponse;
import com.justjava.humanresource.loan.enums.LoanAttachmentType;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface LoanAttachmentService {

    /** Download payload; the controller sets headers from filename/contentType. */
    record LoanAttachmentDownload(Resource resource, String filename, String contentType) {
    }

    /** Applicant only, while the application is DRAFT or RETURNED_FOR_CORRECTION. */
    LoanAttachmentResponse upload(Long loanApplicationId, MultipartFile file, LoanAttachmentType type);

    /** Applicant, HR/admin, Finance, or an assigned custom approver (never another employee's draft). */
    LoanAttachmentDownload download(Long loanApplicationId, Long attachmentId);

    /** Applicant only, while the application is DRAFT or RETURNED_FOR_CORRECTION. */
    void delete(Long loanApplicationId, Long attachmentId);

    /** Same visibility rules as download. */
    List<LoanAttachmentResponse> list(Long loanApplicationId);

    /** Internal: removes files and rows of a hard-deleted draft. No access check. */
    void deleteAllForApplication(Long loanApplicationId);
}
