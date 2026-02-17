package com.justjava.humanresource.hr.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KpiAssignmentItemRequestDTO {

    private Long kpiId;
    private BigDecimal weight;
    private boolean mandatory;
}
