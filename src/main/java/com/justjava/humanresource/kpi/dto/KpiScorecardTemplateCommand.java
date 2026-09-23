package com.justjava.humanresource.kpi.dto;

import com.justjava.humanresource.kpi.enums.KpiFrequency;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class KpiScorecardTemplateCommand {
    private String name;
    private String description;
    private String roleName;
    private Long defaultRubricId;
    private boolean active = true;
    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private String clientKey;
        private String parentClientKey;
        private Long kpiId;
        private BigDecimal weight;
        private boolean mandatory = true;
        private Integer sortOrder;
        private KpiFrequency frequency;
        private Long rubricId;
    }
}
