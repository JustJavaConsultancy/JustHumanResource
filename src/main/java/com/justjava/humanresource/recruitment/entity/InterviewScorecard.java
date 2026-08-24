package com.justjava.humanresource.recruitment.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.recruitment.enums.InterviewRecommendation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter @Entity
@Table(name = "recruitment_interview_scorecards", uniqueConstraints =
        @UniqueConstraint(name = "uk_interview_scorecard_reviewer", columnNames = {"interviewId", "reviewerEmployeeId"}))
public class InterviewScorecard extends BaseEntity {
    @Column(nullable = false) private Long interviewId;
    @Column(nullable = false) private Long reviewerEmployeeId;
    @Column(nullable = false) private Integer overallScore;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private InterviewRecommendation recommendation;
    @Column(length = 3000) private String comments;
    @Column(nullable = false) private LocalDateTime submittedAt;
}
