package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class PromotionActivityResponse {
    Long id;
    PromotionActivityType activityType;
    String actorName;
    String description;
    String ipAddress;
    String userAgent;
    String deviceMetadata;
    LocalDateTime createdAt;
}
