package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.recruitment.enums.AiShortlistingRecommendation;
import com.justjava.humanresource.recruitment.enums.AiShortlistingRunStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "recruitment_ai_shortlisting_runs", indexes = {
        @Index(name = "idx_ai_shortlist_application", columnList = "applicationId"),
        @Index(name = "idx_ai_shortlist_opening", columnList = "jobOpeningId")
})
public class AiShortlistingRun extends BaseEntity {

    @Column(nullable = false)
    private Long applicationId;

    @Column(nullable = false)
    private Long candidateId;

    @Column(nullable = false)
    private Long jobOpeningId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AiShortlistingRunStatus status = AiShortlistingRunStatus.PENDING;

    private Long triggeredByEmployeeId;

    @Column(nullable = false)
    private LocalDateTime triggeredAt;

    private LocalDateTime completedAt;

    @Column(length = 100)
    private String agentName;

    @Column(length = 40)
    private String agentVersion;

    @Column(length = 60)
    private String modelProvider;

    @Column(length = 120)
    private String modelName;

    @Column(length = 1000)
    private String inputDocumentIds;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String jobCriteriaSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private AiShortlistingRecommendation overallRecommendation;

    private Integer overallScore;

    @Column(length = 2000)
    private String summary;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String concerns;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String missingRequirements;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String fairnessWarnings;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String rawAgentOutput;

    @Column(length = 2000)
    private String errorMessage;
}
