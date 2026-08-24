package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.recruitment.enums.ApplicationStatus;
import com.justjava.humanresource.recruitment.enums.RecruitmentStage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Entity
@Table(name = "recruitment_job_applications", indexes = {
        @Index(name = "idx_application_opening_stage", columnList = "jobOpeningId,currentStage"),
        @Index(name = "idx_application_company_status", columnList = "companyId,status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_application_number", columnNames = "applicationNumber"),
        @UniqueConstraint(name = "uk_candidate_job_application", columnNames = {"candidateId", "jobOpeningId"})
})
public class JobApplication extends BaseEntity {
    @Column(nullable = false, length = 30) private String applicationNumber;
    @Column(nullable = false) private Long candidateId;
    @Column(nullable = false) private Long jobOpeningId;
    @Column(nullable = false) private Long companyId;
    private Long recruiterEmployeeId;
    @Column(length = 50) private String applicationSource;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RecruitmentStage currentStage = RecruitmentStage.APPLICATION;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ApplicationStatus status = ApplicationStatus.DRAFT;
    private LocalDateTime submittedAt;
    @Column(length = 1000) private String rejectionReason;
    @Column(length = 1000) private String withdrawalReason;
    @Column(length = 100) private String workflowInstanceId;
    private Long hiredEmployeeId;
    @Column(nullable = false, length = 64) private String accessTokenHash;
    private LocalDateTime accessTokenExpiresAt;
}
