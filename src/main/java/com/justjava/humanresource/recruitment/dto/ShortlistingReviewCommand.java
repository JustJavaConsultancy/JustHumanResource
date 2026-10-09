package com.justjava.humanresource.recruitment.dto;

import com.justjava.humanresource.recruitment.enums.ShortlistingReviewDecision;
import lombok.Data;

@Data
public class ShortlistingReviewCommand {
    private Long runId;
    private ShortlistingReviewDecision decision = ShortlistingReviewDecision.NO_DECISION;
    private String comment;
    private boolean aiRecommendationAccepted;
    private String overrideReason;
}
