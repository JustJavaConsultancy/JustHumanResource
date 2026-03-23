package com.justjava.humanresource.kpi.report.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class KpiDashboardDTO {

    private List<TopPerformerDTO> topPerformers;
    private List<TopPerformerDTO> bottomPerformers;

    private KpiDistributionDTO distribution;
}