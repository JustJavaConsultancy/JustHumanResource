package com.justjava.humanresource.communication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BroadcastCommand(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 5000) String content
) {
}
