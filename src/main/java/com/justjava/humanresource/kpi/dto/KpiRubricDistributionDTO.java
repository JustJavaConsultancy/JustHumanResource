package com.justjava.humanresource.kpi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class KpiRubricDistributionDTO {
    private Long bandId;
    private String bandLabel;
    private BigDecimal minScore;
    private BigDecimal maxScore;
    private long selfSelections;
    private long managerSelections;
    private long totalSelections;
}
