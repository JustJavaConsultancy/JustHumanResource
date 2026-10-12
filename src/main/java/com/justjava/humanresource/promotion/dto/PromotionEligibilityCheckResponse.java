package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.enums.PromotionEligibilitySeverity;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class PromotionEligibilityCheckResponse {
    Long id;
    String checkCode;
    String checkName;
    boolean passed;
    PromotionEligibilitySeverity severity;
    String message;
    boolean overrideAllowed;
    boolean overridden;
    String overriddenByName;
    String overrideReason;
    LocalDateTime overriddenAt;
}
