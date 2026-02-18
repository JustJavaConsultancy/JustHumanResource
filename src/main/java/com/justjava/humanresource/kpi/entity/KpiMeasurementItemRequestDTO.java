package com.justjava.humanresource.kpi.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KpiMeasurementItemRequestDTO {

    private Long kpiId;
    private BigDecimal actualValue;
}
