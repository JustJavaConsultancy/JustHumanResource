package com.justjava.humanresource.kpi.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class KpiScoringRubricCommand {
    private String name;
    private String description;
    private boolean active = true;
    private List<Band> bands = new ArrayList<>();

    @Data
    public static class Band {
        private String label;
        private BigDecimal minScore;
        private BigDecimal maxScore;
        private BigDecimal numericScore;
        private String description;
        private Integer sortOrder;
    }
}
