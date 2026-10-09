package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.recruitment.enums.ShortlistingReviewDecision;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "recruitment_ai_shortlisting_review_decisions",
        indexes = @Index(name = "idx_ai_shortlist_decision_application", columnList = "applicationId"))
public class AiShortlistingReviewDecision extends BaseEntity {

    @Column(nullable = false)
    private Long applicationId;

    @Column(nullable = false)
    private Long aiShortlistingRunId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShortlistingReviewDecision decision = ShortlistingReviewDecision.NO_DECISION;

    private Long decidedByEmployeeId;

    @Column(nullable = false)
    private LocalDateTime decidedAt;

    @Column(length = 2000)
    private String comment;

    @Column(nullable = false)
    private boolean aiRecommendationAccepted;

    @Column(length = 2000)
    private String overrideReason;
}
