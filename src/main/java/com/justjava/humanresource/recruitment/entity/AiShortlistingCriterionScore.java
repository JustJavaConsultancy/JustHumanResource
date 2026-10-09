package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.recruitment.enums.ShortlistingCriterionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "recruitment_ai_shortlisting_criterion_scores",
        indexes = @Index(name = "idx_ai_shortlist_score_run", columnList = "runId"))
public class AiShortlistingCriterionScore extends BaseEntity {

    @Column(nullable = false)
    private Long runId;

    @Column(nullable = false, length = 200)
    private String criterionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ShortlistingCriterionType criterionType = ShortlistingCriterionType.OTHER;

    @Column(nullable = false)
    private boolean required;

    private Integer score;

    private Integer maxScore;

    @Column(length = 2000)
    private String evidence;

    @Column(length = 2000)
    private String missingEvidence;

    @Column(length = 2000)
    private String comment;
}
