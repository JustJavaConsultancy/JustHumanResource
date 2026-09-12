package com.justjava.humanresource.employeeexit.dto;

import com.justjava.humanresource.employeeexit.enums.SettlementLineType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExitPackageComponentCommand {
    @NotBlank private String componentCode;
    @NotBlank private String name;
    @NotNull private SettlementLineType lineType;
    private boolean earning;
    private boolean taxable;
    private boolean pensionable;
    private boolean active = true;
}
