package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.kpi.dto.EmployeeAppraisalDTO;
import com.justjava.humanresource.kpi.entity.*;
import com.justjava.humanresource.kpi.enums.AppraisalOutcome;
import com.justjava.humanresource.kpi.repositories.AppraisalCycleRepository;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import com.justjava.humanresource.kpi.repositories.KpiMeasurementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AppraisalService {

    private static final BigDecimal KPI_WEIGHT = BigDecimal.valueOf(0.7);
    private static final BigDecimal MANAGER_WEIGHT = BigDecimal.valueOf(0.3);

    private final EmployeeRepository employeeRepository;
    private final KpiAssignmentRepository assignmentRepository;
    private final KpiMeasurementRepository measurementRepository;
    private final EmployeeAppraisalRepository appraisalRepository;
    private final AppraisalCycleRepository cycleRepository;

    /* =========================================================
       STEP 1 — CREATE DRAFT APPRAISAL (KPI SCORE ONLY)
       ========================================================= */

    public EmployeeAppraisal createDraftAppraisal(
            Long employeeId,
            AppraisalCycle cycle
    ) {

        if (appraisalRepository.existsByEmployee_IdAndCycle_Id(
                employeeId, cycle.getId())) {
            throw new IllegalStateException(
                    "Appraisal already exists for employee and cycle.");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new IllegalStateException("Employee not found: " + employeeId)
                );

        BigDecimal kpiScore = calculateKpiScore(employeeId, cycle);

        EmployeeAppraisal appraisal = EmployeeAppraisal.builder()
                .employee(employee)
                .cycle(cycle)
                .kpiScore(kpiScore)
                .build();

        log.info("Draft appraisal created for employee {} with kpiScore={}",
                employeeId, kpiScore);

        return appraisalRepository.save(appraisal);
    }

    /* =========================================================
       STEP 2 — FINALIZE APPRAISAL (ADD MANAGER SCORE)
       ========================================================= */

    public EmployeeAppraisal finalizeAppraisal(
            Long appraisalId,
            BigDecimal managerScore,
            String managerComment
    ) {

        EmployeeAppraisal appraisal =
                appraisalRepository.findById(appraisalId)
                        .orElseThrow(() ->
                                new IllegalStateException("Appraisal not found: " + appraisalId)
                        );

        if (appraisal.getCompletedAt() != null) {
            throw new IllegalStateException("Appraisal already finalized.");
        }

        BigDecimal finalScore = calculateFinalScore(
                appraisal.getKpiScore(),
                managerScore
        );

        appraisal.setManagerScore(managerScore);
        appraisal.setFinalScore(finalScore);
        appraisal.setOutcome(determineOutcome(finalScore));
        appraisal.setManagerComment(managerComment);
        appraisal.setCompletedAt(LocalDateTime.now());

        log.info("Appraisal finalized for employee {} with finalScore={}",
                appraisal.getEmployee().getId(), finalScore);

        return appraisalRepository.save(appraisal);
    }
    @Transactional(readOnly = true)
    public List<EmployeeAppraisalDTO> getAllActiveAppraisals() {

        List<EmployeeAppraisal> appraisals =
                appraisalRepository.findAllActiveAppraisals();

        return appraisals.stream()
                .map(a -> EmployeeAppraisalDTO.builder()
                        .appraisalId(a.getId())
                        .employeeId(a.getEmployee().getId())
                        .employeeName(a.getEmployee().getFullName())
                        .cycleId(a.getCycle().getId())
                        .cycleName(a.getCycle().getName())
                        .kpiScore(a.getKpiScore())
                        .managerScore(a.getManagerScore())
                        .managerComment(a.getManagerComment())
                        .selfScore(a.getSelfScore())
                        .selfComment(a.getSelfComment())
                        .finalScore(a.getFinalScore())
                        .outcome(a.getOutcome())
                        .completedAt(a.getCompletedAt())
                        .build()
                )
                .toList();
    }

    /* =========================================================
       CORE KPI CALCULATION (Single Source of Truth)
       ========================================================= */

    private BigDecimal calculateKpiScore(
            Long employeeId,
            AppraisalCycle cycle
    ) {

        YearMonth start = cycle.getStartPeriod();
        YearMonth end = cycle.getEndPeriod();

        List<KpiMeasurement> measurements =
                measurementRepository
                        .findByEmployee_IdAndPeriodBetween(
                                employeeId,
                                start,
                                end
                        );

        if (measurements.isEmpty()) {
            throw new IllegalStateException(
                    "No KPI measurements found for employee "
                            + employeeId + " in cycle " + cycle.getName()
            );
        }

        BigDecimal totalScore = BigDecimal.ZERO;

        for (KpiMeasurement measurement : measurements) {
            totalScore = totalScore.add(measurement.getScore());
        }

        return totalScore
                .divide(BigDecimal.valueOf(measurements.size()),
                        2,
                        RoundingMode.HALF_UP);
    }

    /* =========================================================
       FINAL SCORE CALCULATION
       ========================================================= */

    private BigDecimal calculateFinalScore(
            BigDecimal kpiScore,
            BigDecimal managerScore
    ) {

        if (managerScore == null) {
            return kpiScore;
        }

        return kpiScore.multiply(KPI_WEIGHT)
                .add(managerScore.multiply(MANAGER_WEIGHT))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /* =========================================================
       OUTCOME DETERMINATION
       ========================================================= */

    private AppraisalOutcome determineOutcome(BigDecimal score) {

        if (score.compareTo(BigDecimal.valueOf(85)) >= 0) {
            return AppraisalOutcome.EXCEEDS_EXPECTATION;
        } else if (score.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return AppraisalOutcome.MEETS_EXPECTATION;
        } else if (score.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return AppraisalOutcome.NEEDS_IMPROVEMENT;
        } else {
            return AppraisalOutcome.UNDERPERFORMING;
        }
    }


    public AppraisalCycle createAppraisalCycle(
            int year,
            int quarter
    ) {

        if (quarter < 1 || quarter > 4) {
            throw new IllegalArgumentException("Quarter must be between 1 and 4.");
        }

        if (cycleRepository.existsByYearAndQuarter(year, quarter)) {
            throw new IllegalStateException(
                    "AppraisalCycle already exists for year "
                            + year + " and quarter " + quarter
            );
        }

        int startMonth = (quarter - 1) * 3 + 1;

        YearMonth startPeriod = YearMonth.of(year, startMonth);
        YearMonth endPeriod = startPeriod.plusMonths(2);

        String name = year + " Q" + quarter;

        AppraisalCycle cycle = AppraisalCycle.builder()
                .name(name)
                .year(year)
                .quarter(quarter)
                .startPeriod(startPeriod)
                .endPeriod(endPeriod)
                .active(true)
                .completed(false)
                .totalEmployees(0)
                .processedEmployees(0)
                .startedAt(LocalDateTime.now())
                .build();

        log.info("AppraisalCycle manually created: {}", name);

        return cycleRepository.save(cycle);
    }

}

//Sample for Cycle
/*
        {
            "year": 2026,
            "quarter": 2
        }
*/

//Sample for Employee Appraisal
/*      {
            "employeeId": 12,
            "cycleId": 3
        }
        */
