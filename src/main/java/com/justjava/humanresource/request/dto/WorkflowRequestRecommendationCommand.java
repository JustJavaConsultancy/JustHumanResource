package com.justjava.humanresource.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkflowRequestRecommendationCommand(
        @NotBlank
        @Size(max = 3000)
        String text) {
}
