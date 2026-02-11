package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.entity.KpiMeasurement;
import com.justjava.humanresource.kpi.repositories.KpiMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class KpiMeasurementService {

    private final KpiMeasurementRepository repository;

    public KpiMeasurement recordMeasurement(
            Employee employee,
            KpiDefinition kpi,
            BigDecimal actualValue,
            YearMonth period
    ) {

        BigDecimal score = calculateScore(actualValue, kpi.getTargetValue());

        return repository.save(
                KpiMeasurement.builder()
                        .employee(employee)
                        .kpi(kpi)
                        .actualValue(actualValue)
                        .score(score)
                        .period(period)
                        .recordedAt(LocalDateTime.now())
                        .build()
        );
    }

    private BigDecimal calculateScore(BigDecimal actual, BigDecimal target) {
        if (target.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return actual
                .divide(target, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .min(BigDecimal.valueOf(100));
    }
}

