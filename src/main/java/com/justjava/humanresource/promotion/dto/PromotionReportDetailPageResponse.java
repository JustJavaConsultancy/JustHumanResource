package com.justjava.humanresource.promotion.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PromotionReportDetailPageResponse {
    List<PromotionSummaryResponse> content;
    int pageNumber;
    int pageSize;
    long totalElements;
    int totalPages;
}
