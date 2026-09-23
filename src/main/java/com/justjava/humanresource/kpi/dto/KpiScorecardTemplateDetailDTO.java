package com.justjava.humanresource.kpi.dto;

import com.justjava.humanresource.kpi.enums.KpiFrequency;
import com.justjava.humanresource.kpi.enums.KpiScorecardTemplateStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class KpiScorecardTemplateDetailDTO {
    private Long id;
    private String name;
    private String description;
    private String roleName;
    private BigDecimal totalWeight;
    private boolean active;
    private KpiScorecardTemplateStatus status;
    private Long defaultRubricId;
    private String sourceType;
    private String sourceFileName;
    private String importedBy;
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private Long id;
        private String clientKey;
        private String parentClientKey;
        private Long kpiId;
        private BigDecimal weight;
        private boolean mandatory;
        private Integer sortOrder;
        private KpiFrequency frequency;
        private Long rubricId;
    }
}
