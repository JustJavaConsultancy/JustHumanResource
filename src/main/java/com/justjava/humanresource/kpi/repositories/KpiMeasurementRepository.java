package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.kpi.entity.KpiDefinition;
import com.justjava.humanresource.kpi.entity.KpiMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface KpiMeasurementRepository
        extends JpaRepository<KpiMeasurement, Long> {

    /* =========================================================
       BASIC LOOKUPS
       ========================================================= */

    List<KpiMeasurement> findByEmployee_IdAndPeriod(
            Long employeeId, YearMonth period);

    List<KpiMeasurement> findByEmployee_Id(
            Long employeeId);

    /* =========================================================
       KPI-SPECIFIC LOOKUP
       ========================================================= */

    Optional<KpiMeasurement> findByEmployeeAndKpiAndPeriod(
            Employee employee,
            KpiDefinition kpi,
            YearMonth period);

    boolean existsByEmployee_IdAndKpi_IdAndPeriod(
            Long employeeId,
            Long kpiId,
            YearMonth period
    );

    /* =========================================================
       APPRAISAL SUPPORT
       ========================================================= */

    @Query("""
           SELECT m
           FROM KpiMeasurement m
           WHERE m.employee.id = :employeeId
           AND m.period = :period
           """)
    List<KpiMeasurement> findAllForAppraisalPeriod(
            @Param("employeeId") Long employeeId,
            @Param("period") YearMonth period
    );

    /* =========================================================
       AGGREGATION SUPPORT (Optional Advanced)
       ========================================================= */

    @Query("""
           SELECT COALESCE(SUM(m.score), 0)
           FROM KpiMeasurement m
           WHERE m.employee.id = :employeeId
           AND m.period = :period
           """)
    Double sumScoresForPeriod(
            @Param("employeeId") Long employeeId,
            @Param("period") YearMonth period
    );

}
