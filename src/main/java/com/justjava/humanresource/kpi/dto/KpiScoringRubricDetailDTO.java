package com.justjava.humanresource.kpi.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class KpiScoringRubricDetailDTO {
    private Long id;
    private String name;
    private String description;
    private boolean active;
    private List<Band> bands;

    @Data
    @Builder
    public static class Band {
        private Long id;
        private String label;
        private BigDecimal minScore;
        private BigDecimal maxScore;
        private BigDecimal numericScore;
        private String description;
        private int sortOrder;
    }
}
