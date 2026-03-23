package com.justjava.humanresource.kpi.report.service.impl;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.entity.KpiAssignment;
import com.justjava.humanresource.kpi.entity.KpiMeasurement;
import com.justjava.humanresource.kpi.report.dto.*;
import com.justjava.humanresource.kpi.report.service.KPIReportService;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.kpi.repositories.KpiAssignmentRepository;
import com.justjava.humanresource.kpi.repositories.KpiMeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.math.RoundingMode;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KPIReportServiceImpl implements KPIReportService {

    private final KpiMeasurementRepository measurementRepository;
    private final KpiAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAppraisalRepository appraisalRepository;

    /* =========================================================
       1. EMPLOYEE KPI SCORECARD
       ========================================================= */

    @Override
    public List<EmployeeKpiScorecardDTO> getEmployeeScorecard(
            Long employeeId,
            YearMonth period
    ) {

        List<KpiMeasurement> measurements =
                measurementRepository.findDetailedByEmployeeAndPeriod(
                        employeeId, period
                );

        return measurements.stream()
                .map(m -> {

/*                    BigDecimal weight = m.getWeight() != null
                            ? m.getWeight()
                            : BigDecimal.ZERO;*/
                    BigDecimal weight =
                            assignmentRepository
                                    .findByEmployee_IdAndKpi_IdAndActiveTrue(
                                            employeeId,
                                            m.getKpi().getId()
                                    )
                                    .map(KpiAssignment::getWeight)
                                    .orElse(BigDecimal.ZERO);

                    BigDecimal weightedScore =
                            m.getScore().multiply(weight);

                    return EmployeeKpiScorecardDTO.builder()
                            .employeeId(m.getEmployee().getId())
                            .employeeName(m.getEmployee().getFullName())
                            .kpiCode(m.getKpi().getCode())
                            .kpiName(m.getKpi().getName())
                            .targetValue(m.getKpi().getTargetValue())
                            .actualValue(m.getActualValue())
                            .score(m.getScore())
                            .weight(weight)
                            .weightedScore(weightedScore)
                            .build();
                })
                .toList();
    }

    /* =========================================================
       2. DEPARTMENT KPI REPORT
       ========================================================= */

    @Override
    public List<DepartmentKpiReportDTO> getDepartmentKpiReport(
            YearMonth period
    ) {

        List<Employee> employees = employeeRepository.findAll();

        return employees.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getDepartment()
                ))
                .entrySet()
                .stream()
                .map(entry -> {

                    Long deptId = entry.getKey().getId();
                    String deptName = entry.getKey().getName();

                    List<Employee> deptEmployees = entry.getValue();

                    BigDecimal totalScore = BigDecimal.ZERO;
                    int count = 0;

                    for (Employee e : deptEmployees) {

                        List<KpiMeasurement> measurements =
                                measurementRepository
                                        .findByEmployee_IdAndPeriod(
                                                e.getId(), period
                                        );

                        for (KpiMeasurement m : measurements) {
                            totalScore = totalScore.add(m.getScore());
                            count++;
                        }
                    }

                    BigDecimal avg =
                            count == 0
                                    ? BigDecimal.ZERO
                                    : totalScore.divide(
                                    BigDecimal.valueOf(count),
                                    2,
                                    RoundingMode.HALF_UP
                            );

                    return DepartmentKpiReportDTO.builder()
                            .departmentId(deptId)
                            .departmentName(deptName)
                            .totalEmployees((long) deptEmployees.size())
                            .averageKpiScore(avg)
                            .build();
                })
                .toList();
    }

    /* =========================================================
       3. APPRAISAL SUMMARY REPORT
       ========================================================= */

    @Override
    public List<AppraisalSummaryDTO> getAppraisalSummary(
            Long cycleId
    ) {

        List<EmployeeAppraisal> appraisals =
                appraisalRepository.findByCycleWithDetails(cycleId);

        return appraisals.stream()
                .map(a -> AppraisalSummaryDTO.builder()
                        .employeeId(a.getEmployee().getId())
                        .employeeName(a.getEmployee().getFullName())
                        .cycleName(a.getCycle().getName())
                        .kpiScore(a.getKpiScore())
                        .managerScore(a.getManagerScore())
                        .finalScore(a.getFinalScore())
                        .outcome(a.getOutcome().name())
                        .build()
                )
                .toList();
    }
    @Override
    public List<EmployeeKpiScorecardDTO> getAllEmployeesScorecard(
            YearMonth period
    ) {

        List<KpiMeasurement> measurements =
                measurementRepository.findAllDetailedByPeriod(period);

        return measurements.stream()
                .map(m -> {

                    BigDecimal weight =
                            assignmentRepository
                                    .findByEmployee_IdAndKpi_IdAndActiveTrue(
                                            m.getEmployee().getId(),
                                            m.getKpi().getId()
                                    )
                                    .map(KpiAssignment::getWeight)
                                    .orElse(BigDecimal.ZERO);

                    BigDecimal weightedScore =
                            m.getScore().multiply(weight);

                    return EmployeeKpiScorecardDTO.builder()
                            .employeeId(m.getEmployee().getId())
                            .employeeName(m.getEmployee().getFullName())
                            .kpiCode(m.getKpi().getCode())
                            .kpiName(m.getKpi().getName())
                            .targetValue(m.getKpi().getTargetValue())
                            .actualValue(m.getActualValue())
                            .score(m.getScore())
                            .weight(weight)
                            .weightedScore(weightedScore)
                            .build();
                })
                .toList();
    }
    @Override
    public List<TopPerformerDTO> getTopPerformersByKpi(
            YearMonth period,
            int limit
    ) {

        List<Object[]> results =
                measurementRepository.findTopPerformersByKpi(
                        period,
                        PageRequest.of(0, limit)
                );

        List<TopPerformerDTO> response = new ArrayList<>();

        int rank = 1;

        for (Object[] row : results) {

            response.add(
                    TopPerformerDTO.builder()
                            .employeeId((Long) row[0])
                            .employeeName((String) row[1])
                            .score(BigDecimal.valueOf((Double) row[2]))
                            .rank(rank++)
                            .build()
            );
        }

        return response;
    }
    @Override
    public List<TopPerformerDTO> getTopPerformersByAppraisal(
            Long cycleId,
            int limit
    ) {

        List<Object[]> results =
                appraisalRepository.findTopPerformersByAppraisal(
                        cycleId,
                        PageRequest.of(0, limit)
                );

        List<TopPerformerDTO> response = new ArrayList<>();

        int rank = 1;

        for (Object[] row : results) {

            response.add(
                    TopPerformerDTO.builder()
                            .employeeId((Long) row[0])
                            .employeeName((String) row[1])
                            .score((BigDecimal) row[2])
                            .rank(rank++)
                            .build()
            );
        }

        return response;
    }
    @Override
    public KpiDashboardDTO getKpiDashboard(
            YearMonth period,
            int topLimit,
            int bottomLimit
    ) {

    /* =============================
       TOP PERFORMERS
       ============================= */
        List<Object[]> topResults =
                measurementRepository.findTopPerformersByKpi(
                        period,
                        PageRequest.of(0, topLimit)
                );

        List<TopPerformerDTO> topPerformers = mapToTopPerformers(topResults);

    /* =============================
       BOTTOM PERFORMERS
       ============================= */
        List<Object[]> bottomResults =
                measurementRepository.findBottomPerformersByKpi(
                        period,
                        PageRequest.of(0, bottomLimit)
                );

        List<TopPerformerDTO> bottomPerformers = mapToTopPerformers(bottomResults);

    /* =============================
       DISTRIBUTION
       ============================= */
/*        Object[] dist =
                measurementRepository.getScoreDistribution(String.valueOf(period));*/

        Object[] raw = measurementRepository.getScoreDistribution(period.toString());

        Object[] dist;

// 🔥 unwrap if nested
        if (raw.length == 1 && raw[0] instanceof Object[]) {
            dist = (Object[]) raw[0];
        } else {
            dist = raw;
        }

        System.out.println(" ");
        KpiDistributionDTO distribution =
                KpiDistributionDTO.builder()
                        .excellent(getLong(dist, 0))
                        .good(getLong(dist, 1))
                        .average(getLong(dist, 2))
                        .poor(getLong(dist, 3))
                        .build();

        return KpiDashboardDTO.builder()
                .topPerformers(topPerformers)
                .bottomPerformers(bottomPerformers)
                .distribution(distribution)
                .build();
    }
    private List<TopPerformerDTO> mapToTopPerformers(List<Object[]> rows) {

        List<TopPerformerDTO> list = new ArrayList<>();
        int rank = 1;

        for (Object[] row : rows) {

            list.add(
                    TopPerformerDTO.builder()
                            .employeeId((Long) row[0])
                            .employeeName((String) row[1])
                            .score(BigDecimal.valueOf((Double) row[2]))
                            .rank(rank++)
                            .build()
            );
        }

        return list;
    }

    private long getLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
    private long getLong(Object[] arr, int index) {

        if (arr == null || arr.length <= index || arr[index] == null) {
            return 0L;
        }

        Object value = arr[index];

        if (value instanceof Number number) {
            return number.longValue();
        }

        // 🔥 Defensive fallback
        return Long.parseLong(value.toString());
    }
}