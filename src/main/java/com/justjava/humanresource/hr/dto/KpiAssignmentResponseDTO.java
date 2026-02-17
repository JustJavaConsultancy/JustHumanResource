package com.justjava.humanresource.hr.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class KpiAssignmentResponseDTO {

    private Long assignmentId;
    private Long kpiId;
    private String kpiCode;
    private BigDecimal weight;
    private boolean mandatory;
}
