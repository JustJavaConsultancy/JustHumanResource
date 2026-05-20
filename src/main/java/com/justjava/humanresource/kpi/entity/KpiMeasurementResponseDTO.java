package com.justjava.humanresource.kpi.entity;

import com.justjava.humanresource.hr.entity.Employee;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.YearMonth;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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