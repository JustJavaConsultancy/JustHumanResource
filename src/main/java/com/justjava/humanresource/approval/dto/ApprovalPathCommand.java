package com.justjava.humanresource.approval.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ApprovalPathCommand {
    @NotBlank
    private String name;

    private String description;

    private boolean enabled = true;

    @Valid
    @NotEmpty
    private List<Step> steps;

    @Data
    public static class Step {
        @NotNull
        private Long approverEmployeeId;
    }
}
