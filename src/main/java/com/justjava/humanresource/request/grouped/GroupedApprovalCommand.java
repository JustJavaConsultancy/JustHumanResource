package com.justjava.humanresource.request.grouped;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record GroupedApprovalCommand(
        @NotBlank @Size(max=100) String name,
        @Size(max=500) String description,
        @NotEmpty @Size(max=20) List<@Valid Level> levels) {
    public record Level(@NotBlank @Size(max=100) String name,
                        @NotNull ApprovalPolicy policy,
                        @NotEmpty @Size(max=50) List<@NotNull Long> employeeIds) {}
}
