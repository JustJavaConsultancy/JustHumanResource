package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.kpi.entity.*;
import com.justjava.humanresource.kpi.enums.AppraisalOutcome;
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
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AppraisalService {

    private final EmployeeRepository employeeRepository;
    private final KpiAssignmentRepository assignmentRepository;
    private final KpiMeasurementRepository measurementRepository;
    private final EmployeeAppraisalRepository appraisalRepository;

    public EmployeeAppraisal generateAppraisal(
            Long employeeId,
            AppraisalCycle cycle,
            BigDecimal managerScore,
            String managerComment
    ) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow();

        YearMonth period = cycle.getPeriod();
        LocalDate referenceDate = period.atEndOfMonth();

        // 1️⃣ Get effective KPI assignments
        List<KpiAssignment> assignments =
                assignmentRepository.findEffectiveAssignmentsForEmployee(
                        employeeId,
                        //employee.getJobStep().getId(),
                        referenceDate
                );

        if (assignments.isEmpty()) {
            throw new IllegalStateException(
                    "No effective KPI assignments found for employee: " + employeeId);
        }

        // 2️⃣ Fetch measurements for the cycle
        List<KpiMeasurement> measurements =
                measurementRepository.findByEmployee_IdAndPeriod(
                        employeeId,
                        period
                );

        BigDecimal weightedTotal = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (KpiAssignment assignment : assignments) {

            BigDecimal weight = assignment.getWeight() != null
                    ? assignment.getWeight()
                    : BigDecimal.ZERO;

            totalWeight = totalWeight.add(weight);

            KpiMeasurement measurement =
                    measurements.stream()
                            .filter(m -> m.getKpi().getId()
                                    .equals(assignment.getKpi().getId()))
                            .findFirst()
                            .orElse(null);

            BigDecimal score = measurement != null
                    ? measurement.getScore()
                    : BigDecimal.ZERO;

            weightedTotal = weightedTotal.add(
                    score.multiply(weight)
            );
        }

        BigDecimal kpiScore;

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            kpiScore = BigDecimal.ZERO;
        } else {
            kpiScore = weightedTotal
                    .divide(totalWeight, 2, RoundingMode.HALF_UP);
        }

        // 3️⃣ Combine with manager score
        BigDecimal finalScore = calculateFinalScore(kpiScore, managerScore);

        AppraisalOutcome outcome = determineOutcome(finalScore);

        EmployeeAppraisal appraisal = EmployeeAppraisal.builder()
                .employee(employee)
                .cycle(cycle)
                .kpiScore(kpiScore)
                .managerScore(managerScore)
                .finalScore(finalScore)
                .outcome(outcome)
                .managerComment(managerComment)
                .completedAt(java.time.LocalDateTime.now())
                .build();

        log.info("Generated appraisal for employee {} with finalScore={}",
                employeeId, finalScore);

        return appraisalRepository.save(appraisal);
    }

    /* =========================================================
       FINAL SCORE CALCULATION
       Example: 70% KPI, 30% Manager
       ========================================================= */

    private BigDecimal calculateFinalScore(
            BigDecimal kpiScore,
            BigDecimal managerScore
    ) {

        if (managerScore == null) {
            return kpiScore;
        }

        return kpiScore.multiply(BigDecimal.valueOf(0.7))
                .add(managerScore.multiply(BigDecimal.valueOf(0.3)))
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
}
