package com.justjava.humanresource.loan.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.loan.enums.LoanAttachmentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "employee_loan_attachments",
        indexes = @Index(name = "idx_loan_attachment_application", columnList = "loan_application_id")
)
public class EmployeeLoanAttachment extends BaseEntity {

    @Column(name = "loan_application_id", nullable = false)
    private Long loanApplicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanAttachmentType attachmentType = LoanAttachmentType.SUPPORTING_DOCUMENT;

    @Column(nullable = false)
    private String originalFilename;

    private String contentType;
    private Long fileSize;

    /** Storage path or key; follow the project's existing file-storage convention. */
    @Column(nullable = false, length = 1000)
    private String storagePath;

    private Long uploadedByEmployeeId;
}
