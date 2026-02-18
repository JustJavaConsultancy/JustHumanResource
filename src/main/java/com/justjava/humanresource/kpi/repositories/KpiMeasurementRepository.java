package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Repository
public interface KpiMeasurementRepository
        extends JpaRepository<KpiMeasurement, Long> {

    /* =========================================================
       CORE LOOKUPS
       ========================================================= */

    List<KpiMeasurement> findByEmployee_IdAndPeriod(
            Long employeeId,
            YearMonth period
    );

    boolean existsByEmployee_IdAndKpi_IdAndPeriod(
            Long employeeId,
            Long kpiId,
            YearMonth period
    );

    List<KpiMeasurement> findByEmployee_IdAndPeriodBetween(
            Long employeeId,
            YearMonth start,
            YearMonth end
    );

    /* =========================================================
       JOBSTEP-SCOPED LOOKUP (FIXED — NO findAll())
       ========================================================= */

    List<KpiMeasurement> findByEmployee_JobStep_IdAndPeriod(
            Long jobStepId,
            YearMonth period
    );
    @Query("""
       SELECT m
       FROM KpiMeasurement m
       JOIN KpiAssignment a
            ON a.kpi.id = m.kpi.id
       WHERE m.period = :period
       AND a.active = true
       AND (a.validFrom IS NULL OR a.validFrom <= :referenceDate)
       AND (a.validTo IS NULL OR a.validTo >= :referenceDate)
       """)
    List<KpiMeasurement> findAllEffectiveMeasurementsForPeriod(
            @Param("period") YearMonth period,
            @Param("referenceDate") LocalDate referenceDate
    );

}
