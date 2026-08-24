package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.recruitment.enums.JobOpeningStatus;
import com.justjava.humanresource.request.enums.RequisitionReason;
import com.justjava.humanresource.request.enums.StaffEmploymentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @Entity
@Table(name = "recruitment_job_openings", indexes = {
        @Index(name = "idx_job_opening_company_status", columnList = "companyId,status"),
        @Index(name = "idx_job_opening_public_slug", columnList = "publicSlug")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_job_opening_staff_request", columnNames = "staffRequisitionRequestId"),
        @UniqueConstraint(name = "uk_job_opening_public_slug", columnNames = "publicSlug")
})
public class JobOpening extends BaseEntity {
    @Column(nullable = false) private Long staffRequisitionRequestId;
    @Column(nullable = false) private Long companyId = 1L;
    @Column(nullable = false) private Long departmentId;
    private Long jobGradeId;
    @Column(nullable = false, length = 200) private String jobTitle;
    @Column(nullable = false) private Integer numberOfPositions;
    @Column(nullable = false) private Integer positionsFilled = 0;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private StaffEmploymentType employmentType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RequisitionReason requisitionReason;
    private Long replacementEmployeeId;
    private LocalDate targetStartDate;
    @Column(precision = 19, scale = 2) private BigDecimal estimatedMonthlyCost;
    @Column(nullable = false, length = 2000) private String reasonForHire;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(columnDefinition = "TEXT") private String responsibilities;
    @Column(columnDefinition = "TEXT") private String requirements;
    @Column(columnDefinition = "TEXT") private String preferredQualifications;
    private String location;
    private String workArrangement;
    private LocalDate applicationOpenDate;
    private LocalDate applicationCloseDate;
    @Column(nullable = false, length = 160) private String publicSlug;
    private Long assignedRecruiterEmployeeId;
    @Column(nullable = false) private Long hiringManagerEmployeeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private JobOpeningStatus status = JobOpeningStatus.DRAFT;
    @Column(length = 100) private String workflowInstanceId;
}
