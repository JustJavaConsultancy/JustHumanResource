package com.justjava.humanresource.request.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FinanceRequestForwardCommand {
    @NotNull(message = "A recipient employee must be selected.")
    private Long toEmployeeId;

    @Size(max = 2000)
    private String comment;
}