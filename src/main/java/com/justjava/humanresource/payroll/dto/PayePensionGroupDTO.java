package com.justjava.humanresource.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayePensionGroupDTO {
    private String groupName;
    private int employeeCount;

    @Builder.Default
    private BigDecimal totalPaye = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalEmployeePension = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalEmployerPension = BigDecimal.ZERO;

    @Builder.Default
    private List<PayePensionLineDTO> employees = new ArrayList<>();
}