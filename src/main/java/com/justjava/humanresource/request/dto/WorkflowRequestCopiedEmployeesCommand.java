package com.justjava.humanresource.request.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WorkflowRequestCopiedEmployeesCommand(
        @NotNull @Size(max = 50) List<@NotNull @Positive Long> employeeIds) {
}
