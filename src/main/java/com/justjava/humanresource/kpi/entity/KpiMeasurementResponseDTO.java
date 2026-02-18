package com.justjava.humanresource.kpi.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.YearMonth;

@Data
@Builder
public class KpiMeasurementResponseDTO {

    private Long measurementId;
    private Long kpiId;
    private String kpiCode;
    private BigDecimal actualValue;
    private BigDecimal score;
    private YearMonth period;
}
