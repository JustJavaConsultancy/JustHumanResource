package com.justjava.humanresource.communication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GroupMessageCommand(
        @NotNull Long groupId,
        @NotBlank @Size(max = 2000) String content
) {
}
