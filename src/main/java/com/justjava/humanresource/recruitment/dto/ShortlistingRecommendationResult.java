package com.justjava.humanresource.recruitment.dto;

import com.justjava.humanresource.recruitment.enums.AiShortlistingRecommendation;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ShortlistingRecommendationResult {
    private AiShortlistingRecommendation overallRecommendation = AiShortlistingRecommendation.INSUFFICIENT_INFORMATION;
    private Integer overallScore;
    private String summary;
    private List<String> strengths = new ArrayList<>();
    private List<String> concerns = new ArrayList<>();
    private List<String> missingRequirements = new ArrayList<>();
    private List<String> fairnessWarnings = new ArrayList<>();
    private List<ShortlistingCriterionScoreResult> criterionScores = new ArrayList<>();
    private String rawOutput;
}
