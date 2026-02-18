package com.justjava.humanresource.hr.dto;

import com.justjava.humanresource.kpi.enums.KpiUnit;
import jdk.jfr.Category;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Locale;

@Data
@Builder
public class KpiAssignmentResponseDTO {

    private Long assignmentId;
    private Long kpiId;
    private String kpiCode;
    private BigDecimal weight;
    private boolean mandatory;
    private String name;
    private BigDecimal targetValue;
    private Category category;
    private KpiUnit kpiUnit;
}
