package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.promotion.dto.PromotionApprovalDurationReportResponse;
import com.justjava.humanresource.promotion.dto.PromotionDepartmentReportResponse;
import com.justjava.humanresource.promotion.dto.PromotionReportDetailPageResponse;
import com.justjava.humanresource.promotion.dto.PromotionSalaryImpactReportResponse;
import com.justjava.humanresource.promotion.dto.PromotionStatusReportResponse;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionReportService {

    private static final Set<PromotionStatus> SALARY_IMPACT_STATUSES =
            Set.of(PromotionStatus.APPROVED, PromotionStatus.IMPLEMENTED);

    private final PromotionRequestRepository requestRepository;
    private final PromotionAuthorizationService authorizationService;
    private final PromotionMapper mapper;

    public List<PromotionStatusReportResponse> statusSummary() {
        return statusSummary(null, null);
    }

    public List<PromotionStatusReportResponse> statusSummary(LocalDate startDate, LocalDate endDate) {
        authorizationService.requireHrFinanceOrAdmin();
        Map<PromotionStatus, Long> counts = new EnumMap<>(PromotionStatus.class);
        reportRequests(startDate, endDate).forEach(request ->
                counts.merge(request.getStatus(), 1L, Long::sum));
        List<PromotionStatusReportResponse> response = new ArrayList<>();
        for (PromotionStatus status : PromotionStatus.values()) {
            response.add(PromotionStatusReportResponse.builder()
                    .status(status)
                    .count(counts.getOrDefault(status, 0L))
                    .build());
        }
        return response;
    }

    public List<PromotionDepartmentReportResponse> departmentSummary() {
        return departmentSummary(null, null);
    }

    public List<PromotionDepartmentReportResponse> departmentSummary(LocalDate startDate, LocalDate endDate) {
        authorizationService.requireHrFinanceOrAdmin();
        Map<DepartmentKey, List<PromotionRequest>> byDepartment = new LinkedHashMap<>();
        reportRequests(startDate, endDate).forEach(request -> {
            Department department = request.getProposedDepartment() != null
                    ? request.getProposedDepartment()
                    : request.getCurrentDepartment();
            DepartmentKey key = department == null
                    ? new DepartmentKey(null, "Unassigned")
                    : new DepartmentKey(department.getId(), department.getName());
            byDepartment.computeIfAbsent(key, ignored -> new ArrayList<>()).add(request);
        });
        return byDepartment.entrySet().stream()
                .map(entry -> departmentReport(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(PromotionDepartmentReportResponse::getDepartmentName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    public PromotionSalaryImpactReportResponse salaryImpact() {
        return salaryImpact(null, null);
    }

    public PromotionSalaryImpactReportResponse salaryImpact(LocalDate startDate, LocalDate endDate) {
        authorizationService.requireHrFinanceOrAdmin();
        List<PromotionRequest> requests = reportRequests(startDate, endDate);
        List<PromotionRequest> impactRequests = requests.stream()
                .filter(request -> SALARY_IMPACT_STATUSES.contains(request.getStatus()))
                .toList();
        BigDecimal monthlyImpact = sum(impactRequests, PromotionRequest::getMonthlyPayrollImpact);
        BigDecimal annualImpact = sum(impactRequests, PromotionRequest::getAnnualPayrollImpact);
        BigDecimal averageIncreasePercent = average(impactRequests, PromotionRequest::getSalaryIncreasePercent);
        return PromotionSalaryImpactReportResponse.builder()
                .requestCount(requests.size())
                .approvedOrImplementedCount(impactRequests.size())
                .monthlySalaryImpact(monthlyImpact)
                .annualSalaryImpact(annualImpact)
                .averageSalaryIncreasePercent(averageIncreasePercent)
                .build();
    }

    public PromotionApprovalDurationReportResponse approvalDuration() {
        return approvalDuration(null, null);
    }

    public PromotionApprovalDurationReportResponse approvalDuration(LocalDate startDate, LocalDate endDate) {
        authorizationService.requireHrFinanceOrAdmin();
        List<BigDecimal> durations = reportRequests(startDate, endDate).stream()
                .filter(request -> request.getSubmittedAt() != null && request.getApprovedAt() != null)
                .map(request -> Duration.between(request.getSubmittedAt(), request.getApprovedAt()))
                .map(duration -> BigDecimal.valueOf(duration.toMinutes())
                        .divide(BigDecimal.valueOf(1440), 2, RoundingMode.HALF_UP))
                .toList();
        if (durations.isEmpty()) {
            return PromotionApprovalDurationReportResponse.builder()
                    .approvedRequestCount(0)
                    .averageApprovalDays(BigDecimal.ZERO)
                    .fastestApprovalDays(BigDecimal.ZERO)
                    .slowestApprovalDays(BigDecimal.ZERO)
                    .build();
        }
        BigDecimal total = durations.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return PromotionApprovalDurationReportResponse.builder()
                .approvedRequestCount(durations.size())
                .averageApprovalDays(total.divide(BigDecimal.valueOf(durations.size()), 2, RoundingMode.HALF_UP))
                .fastestApprovalDays(durations.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO))
                .slowestApprovalDays(durations.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO))
                .build();
    }

    public PromotionReportDetailPageResponse details(
            PromotionStatus status,
            Long departmentId,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate approvalStartDate,
            LocalDate approvalEndDate,
            int page,
            int size) {
        authorizationService.requireHrFinanceOrAdmin();
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id")));
        Page<PromotionRequest> result = requestRepository.findAll(
                detailSpecification(status, departmentId, startDate, endDate, approvalStartDate, approvalEndDate),
                pageRequest);
        return PromotionReportDetailPageResponse.builder()
                .content(result.getContent().stream().map(mapper::toSummary).toList())
                .pageNumber(result.getNumber())
                .pageSize(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    public String statusSummaryCsv() {
        return statusSummaryCsv(null, null);
    }

    public String statusSummaryCsv(LocalDate startDate, LocalDate endDate) {
        StringBuilder csv = new StringBuilder("Status,Count\n");
        statusSummary(startDate, endDate).forEach(row -> appendRow(csv, row.getStatus(), row.getCount()));
        return csv.toString();
    }

    public String departmentSummaryCsv() {
        return departmentSummaryCsv(null, null);
    }

    public String departmentSummaryCsv(LocalDate startDate, LocalDate endDate) {
        StringBuilder csv = new StringBuilder("Department,Request Count,Implemented Count,Monthly Salary Impact,Annual Salary Impact\n");
        departmentSummary(startDate, endDate).forEach(row -> appendRow(csv,
                row.getDepartmentName(),
                row.getRequestCount(),
                row.getImplementedCount(),
                row.getMonthlySalaryImpact(),
                row.getAnnualSalaryImpact()));
        return csv.toString();
    }

    public String salaryImpactCsv() {
        return salaryImpactCsv(null, null);
    }

    public String salaryImpactCsv(LocalDate startDate, LocalDate endDate) {
        PromotionSalaryImpactReportResponse report = salaryImpact(startDate, endDate);
        StringBuilder csv = new StringBuilder("Request Count,Approved Or Implemented Count,Monthly Salary Impact,Annual Salary Impact,Average Salary Increase Percent\n");
        appendRow(csv,
                report.getRequestCount(),
                report.getApprovedOrImplementedCount(),
                report.getMonthlySalaryImpact(),
                report.getAnnualSalaryImpact(),
                report.getAverageSalaryIncreasePercent());
        return csv.toString();
    }

    public String approvalDurationCsv() {
        return approvalDurationCsv(null, null);
    }

    public String approvalDurationCsv(LocalDate startDate, LocalDate endDate) {
        PromotionApprovalDurationReportResponse report = approvalDuration(startDate, endDate);
        StringBuilder csv = new StringBuilder("Approved Request Count,Average Approval Days,Fastest Approval Days,Slowest Approval Days\n");
        appendRow(csv,
                report.getApprovedRequestCount(),
                report.getAverageApprovalDays(),
                report.getFastestApprovalDays(),
                report.getSlowestApprovalDays());
        return csv.toString();
    }

    private List<PromotionRequest> reportRequests(LocalDate startDate, LocalDate endDate) {
        List<PromotionRequest> requests = requestRepository.findAll();
        if (startDate == null && endDate == null) {
            return requests;
        }
        return requests.stream()
                .filter(request -> request.getEffectiveDate() != null)
                .filter(request -> startDate == null || !request.getEffectiveDate().isBefore(startDate))
                .filter(request -> endDate == null || !request.getEffectiveDate().isAfter(endDate))
                .toList();
    }

    private Specification<PromotionRequest> detailSpecification(
            PromotionStatus status,
            Long departmentId,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate approvalStartDate,
            LocalDate approvalEndDate) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (departmentId != null) {
                predicates.add(builder.or(
                        builder.equal(root.get("proposedDepartment").get("id"), departmentId),
                        builder.and(
                                builder.isNull(root.get("proposedDepartment")),
                                builder.equal(root.get("currentDepartment").get("id"), departmentId))));
            }
            if (startDate != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("effectiveDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("effectiveDate"), endDate));
            }
            if (approvalStartDate != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("approvedAt"), approvalStartDate.atStartOfDay()));
            }
            if (approvalEndDate != null) {
                predicates.add(builder.lessThan(root.get("approvedAt"), approvalEndDate.plusDays(1).atStartOfDay()));
            }
            return predicates.isEmpty()
                    ? builder.conjunction()
                    : builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private PromotionDepartmentReportResponse departmentReport(DepartmentKey key, List<PromotionRequest> requests) {
        List<PromotionRequest> impactRequests = requests.stream()
                .filter(request -> SALARY_IMPACT_STATUSES.contains(request.getStatus()))
                .toList();
        return PromotionDepartmentReportResponse.builder()
                .departmentId(key.id())
                .departmentName(key.name())
                .requestCount(requests.size())
                .implementedCount(requests.stream()
                        .filter(request -> request.getStatus() == PromotionStatus.IMPLEMENTED)
                        .count())
                .monthlySalaryImpact(sum(impactRequests, PromotionRequest::getMonthlyPayrollImpact))
                .annualSalaryImpact(sum(impactRequests, PromotionRequest::getAnnualPayrollImpact))
                .build();
    }

    private BigDecimal sum(List<PromotionRequest> requests, AmountExtractor extractor) {
        return requests.stream()
                .map(extractor::amount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal average(List<PromotionRequest> requests, AmountExtractor extractor) {
        List<BigDecimal> values = requests.stream()
                .map(extractor::amount)
                .filter(value -> value != null)
                .toList();
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private void appendRow(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvValue(values[i]));
        }
        csv.append('\n');
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private record DepartmentKey(Long id, String name) {
    }

    @FunctionalInterface
    private interface AmountExtractor {
        BigDecimal amount(PromotionRequest request);
    }
}
