package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.hr.entity.Employee;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.YearMonth;

@Data
@Builder
public class KpiMeasurementResponseDTO {

    private String kpiName;
    private Employee employee;
    private Long measurementId;
    private Long kpiId;
    private String kpiCode;
    private BigDecimal actualValue;
    private BigDecimal score;
    private YearMonth period;
}
