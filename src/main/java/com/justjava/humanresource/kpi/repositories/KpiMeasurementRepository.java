package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;

public interface KpiMeasurementRepository
        extends JpaRepository<KpiMeasurement, Long> {

    List<KpiMeasurement> findByEmployee_IdAndPeriod(
            Long employeeId, YearMonth period);
}

