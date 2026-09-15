package com.justjava.humanresource.employeeexit.dto;

import com.justjava.humanresource.employeeexit.enums.ExitPackageCalculationMethod;
import com.justjava.humanresource.employeeexit.enums.ExitType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ExitPackageRuleCommand {
    @NotNull private Long componentId;
    @NotNull private ExitPackageCalculationMethod calculationMethod;
    private BigDecimal fixedAmount;
    private BigDecimal percentage;
    private List<ExitType> appliesToExitTypes;
    private Integer minimumYearsOfService;
    private Integer maximumYearsOfService;
    private boolean requiresManualApproval;
    private boolean active = true;
}