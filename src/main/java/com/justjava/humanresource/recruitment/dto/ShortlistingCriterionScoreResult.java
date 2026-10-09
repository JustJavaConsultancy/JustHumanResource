package com.justjava.humanresource.recruitment.dto;

import com.justjava.humanresource.recruitment.enums.ShortlistingCriterionType;
import lombok.Data;

@Data
public class ShortlistingCriterionScoreResult {
    private String criterionName;
    private ShortlistingCriterionType criterionType = ShortlistingCriterionType.OTHER;
    private boolean required;
    private Integer score;
    private Integer maxScore;
    private String evidence;
    private String missingEvidence;
    private String comment;
}
