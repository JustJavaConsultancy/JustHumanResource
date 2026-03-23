package com.justjava.humanresource.kpi.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TopPerformerDTO {

    private Long employeeId;
    private String employeeName;

    private BigDecimal score;
    private Integer rank;
}