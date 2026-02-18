package com.justjava.humanresource.kpi.entity;

import lombok.Data;

import java.time.YearMonth;
import java.util.List;

@Data
public class KpiBulkMeasurementRequestDTO {

    private Long employeeId;
    private YearMonth period;
    private List<KpiMeasurementItemRequestDTO> measurements;
}
