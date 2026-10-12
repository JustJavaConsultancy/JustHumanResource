package com.justjava.humanresource.promotion.dto;

import com.justjava.humanresource.promotion.enums.PromotionRequestSource;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class PromotionRequestCommand {

    @NotNull
    private Long employeeId;

    @NotNull
    private Long proposedRoleId;

    private Long proposedDepartmentId;
    private Long proposedManagerId;

    private PromotionRequestSource requestSource = PromotionRequestSource.HR_INITIATED;

    @NotNull
    @FutureOrPresent
    private LocalDate effectiveDate;

    @NotBlank
    private String justification;

    private BigDecimal proposedSalary;
}
