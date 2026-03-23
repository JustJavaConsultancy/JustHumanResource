package com.justjava.humanresource.kpi.report.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KpiDistributionDTO {

    private long excellent;  // >= 85
    private long good;       // 70–84
    private long average;    // 50–69
    private long poor;       // < 50
}