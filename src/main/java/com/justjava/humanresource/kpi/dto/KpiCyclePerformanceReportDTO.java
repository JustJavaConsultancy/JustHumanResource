package com.justjava.humanresource.kpi.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class KpiCyclePerformanceReportDTO {
    private Long cycleId;
    private String cycleName;
    private BigDecimal averageScore;
    private long totalAppraisals;
    private long completedAppraisals;
    private BigDecimal completionRate;
    private List<Performer> topPerformers;
    private List<Performer> bottomPerformers;
    private List<CycleTrend> trend;

    @Getter
    @Builder
    public static class Performer {
        private Long appraisalId;
        private Long employeeId;
        private String employeeName;
        private String departmentName;
        private String jobStepName;
        private BigDecimal score;
    }

    @Getter
    @Builder
    public static class CycleTrend {
        private Long cycleId;
        private String cycleName;
        private BigDecimal averageScore;
        private BigDecimal completionRate;
    }
}
