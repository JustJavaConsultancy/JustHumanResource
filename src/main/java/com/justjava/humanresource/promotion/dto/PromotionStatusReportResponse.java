package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.entity.PromotionStatus;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PromotionStatusReportResponse {
    PromotionStatus status;
    long count;
}
