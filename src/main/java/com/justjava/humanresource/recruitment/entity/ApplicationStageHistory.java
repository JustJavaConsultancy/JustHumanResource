package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.recruitment.enums.ApplicationStatus;
import com.justjava.humanresource.recruitment.enums.RecruitmentStage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name = "recruitment_application_stage_history", indexes =
        @Index(name = "idx_application_stage_history", columnList = "applicationId,createdAt"))
public class ApplicationStageHistory extends BaseEntity {
    @Column(nullable = false) private Long applicationId;
    @Enumerated(EnumType.STRING) private RecruitmentStage previousStage;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private RecruitmentStage newStage;
    @Enumerated(EnumType.STRING) private ApplicationStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ApplicationStatus newStatus;
    private Long actorEmployeeId;
    @Column(length = 500) private String reason;
    @Column(length = 2000) private String comments;
    @Column(length = 100) private String flowableTaskId;
}
