package com.justjava.humanresource.request.dto;

import java.time.LocalDateTime;

public record WorkflowRequestRecommendationDTO(
        Long id,
        Long authorEmployeeId,
        String authorEmployeeName,
        String text,
        LocalDateTime createdAt) {
}
