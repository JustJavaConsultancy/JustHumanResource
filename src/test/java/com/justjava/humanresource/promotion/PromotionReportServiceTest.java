package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.promotion.dto.PromotionApprovalDurationReportResponse;
import com.justjava.humanresource.promotion.dto.PromotionDepartmentReportResponse;
import com.justjava.humanresource.promotion.dto.PromotionReportDetailPageResponse;
import com.justjava.humanresource.promotion.dto.PromotionSalaryImpactReportResponse;
import com.justjava.humanresource.promotion.dto.PromotionStatusReportResponse;
import com.justjava.humanresource.promotion.dto.PromotionSummaryResponse;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionMapper;
import com.justjava.humanresource.promotion.service.PromotionReportService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionReportServiceTest {

    @Mock PromotionRequestRepository requestRepository;
    @Mock PromotionAuthorizationService authorizationService;
    @Mock PromotionMapper mapper;

    private PromotionReportService service;

    @BeforeEach
    void setUp() {
        service = new PromotionReportService(requestRepository, authorizationService, mapper);
    }

    @Test
    void buildsStatusSummaryForEveryStatus() {
        when(requestRepository.findAll()).thenReturn(List.of(
                request(PromotionStatus.DRAFT),
                request(PromotionStatus.APPROVED),
                request(PromotionStatus.APPROVED)
        ));

        List<PromotionStatusReportResponse> response = service.statusSummary();

        assertThat(response).hasSize(PromotionStatus.values().length);
        assertThat(count(response, PromotionStatus.DRAFT)).isEqualTo(1);
        assertThat(count(response, PromotionStatus.APPROVED)).isEqualTo(2);
        assertThat(count(response, PromotionStatus.IMPLEMENTED)).isZero();
        verify(authorizationService).requireHrFinanceOrAdmin();
    }

    @Test
    void buildsDepartmentSummaryWithSalaryImpact() {
        Department engineering = department(1L, "Engineering");
        Department operations = department(2L, "Operations");
        PromotionRequest approved = request(PromotionStatus.APPROVED, engineering, "10000.00", "120000.00", "10.00");
        PromotionRequest implemented = request(PromotionStatus.IMPLEMENTED, engineering, "15000.00", "180000.00", "15.00");
        PromotionRequest rejected = request(PromotionStatus.REJECTED, operations, "20000.00", "240000.00", "20.00");
        when(requestRepository.findAll()).thenReturn(List.of(approved, implemented, rejected));

        List<PromotionDepartmentReportResponse> response = service.departmentSummary();

        PromotionDepartmentReportResponse engineeringReport = response.stream()
                .filter(report -> report.getDepartmentId().equals(engineering.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(engineeringReport.getRequestCount()).isEqualTo(2);
        assertThat(engineeringReport.getImplementedCount()).isEqualTo(1);
        assertThat(engineeringReport.getMonthlySalaryImpact()).isEqualByComparingTo("25000.00");
        assertThat(engineeringReport.getAnnualSalaryImpact()).isEqualByComparingTo("300000.00");

        PromotionDepartmentReportResponse operationsReport = response.stream()
                .filter(report -> report.getDepartmentId().equals(operations.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(operationsReport.getMonthlySalaryImpact()).isEqualByComparingTo("0.00");
    }

    @Test
    void buildsSalaryImpactSummaryForApprovedAndImplementedRequestsOnly() {
        when(requestRepository.findAll()).thenReturn(List.of(
                request(PromotionStatus.APPROVED, null, "10000.00", "120000.00", "10.00"),
                request(PromotionStatus.IMPLEMENTED, null, "20000.00", "240000.00", "30.00"),
                request(PromotionStatus.REJECTED, null, "50000.00", "600000.00", "99.00")
        ));

        PromotionSalaryImpactReportResponse response = service.salaryImpact();

        assertThat(response.getRequestCount()).isEqualTo(3);
        assertThat(response.getApprovedOrImplementedCount()).isEqualTo(2);
        assertThat(response.getMonthlySalaryImpact()).isEqualByComparingTo("30000.00");
        assertThat(response.getAnnualSalaryImpact()).isEqualByComparingTo("360000.00");
        assertThat(response.getAverageSalaryIncreasePercent()).isEqualByComparingTo("20.00");
    }

    @Test
    void filtersReportsByEffectiveDateRange() {
        PromotionRequest inRange = request(PromotionStatus.APPROVED, null, "10000.00", "120000.00", "10.00");
        inRange.setEffectiveDate(LocalDate.of(2026, 3, 1));
        PromotionRequest outOfRange = request(PromotionStatus.IMPLEMENTED, null, "50000.00", "600000.00", "50.00");
        outOfRange.setEffectiveDate(LocalDate.of(2026, 5, 1));
        when(requestRepository.findAll()).thenReturn(List.of(inRange, outOfRange));

        PromotionSalaryImpactReportResponse response = service.salaryImpact(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(response.getRequestCount()).isEqualTo(1);
        assertThat(response.getApprovedOrImplementedCount()).isEqualTo(1);
        assertThat(response.getMonthlySalaryImpact()).isEqualByComparingTo("10000.00");
    }

    @Test
    void buildsApprovalDurationSummary() {
        PromotionRequest oneDay = request(PromotionStatus.APPROVED);
        oneDay.setSubmittedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        oneDay.setApprovedAt(LocalDateTime.of(2026, 1, 2, 9, 0));
        PromotionRequest threeDays = request(PromotionStatus.IMPLEMENTED);
        threeDays.setSubmittedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        threeDays.setApprovedAt(LocalDateTime.of(2026, 1, 4, 9, 0));
        when(requestRepository.findAll()).thenReturn(List.of(oneDay, threeDays, request(PromotionStatus.DRAFT)));

        PromotionApprovalDurationReportResponse response = service.approvalDuration();

        assertThat(response.getApprovedRequestCount()).isEqualTo(2);
        assertThat(response.getAverageApprovalDays()).isEqualByComparingTo("2.00");
        assertThat(response.getFastestApprovalDays()).isEqualByComparingTo("1.00");
        assertThat(response.getSlowestApprovalDays()).isEqualByComparingTo("3.00");
    }

    @Test
    void exportsDepartmentSummaryAsCsvWithEscapedValues() {
        Department department = department(1L, "People, Culture");
        when(requestRepository.findAll()).thenReturn(List.of(
                request(PromotionStatus.APPROVED, department, "10000.00", "120000.00", "10.00")
        ));

        String csv = service.departmentSummaryCsv();

        assertThat(csv).startsWith("Department,Request Count,Implemented Count,Monthly Salary Impact,Annual Salary Impact\n");
        assertThat(csv).contains("\"People, Culture\",1,0,10000.00,120000.00\n");
        verify(authorizationService).requireHrFinanceOrAdmin();
    }

    @Test
    void exportsSingleRowReportsAsCsv() {
        PromotionRequest approved = request(PromotionStatus.APPROVED, null, "10000.00", "120000.00", "10.00");
        approved.setSubmittedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        approved.setApprovedAt(LocalDateTime.of(2026, 1, 2, 9, 0));
        when(requestRepository.findAll()).thenReturn(List.of(approved));

        assertThat(service.salaryImpactCsv())
                .contains("Request Count,Approved Or Implemented Count,Monthly Salary Impact,Annual Salary Impact,Average Salary Increase Percent\n")
                .contains("1,1,10000.00,120000.00,10.00\n");
        assertThat(service.approvalDurationCsv())
                .contains("Approved Request Count,Average Approval Days,Fastest Approval Days,Slowest Approval Days\n")
                .contains("1,1.00,1.00,1.00\n");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildsPagedReportDetailsWithClampedPageSize() {
        PromotionRequest request = request(PromotionStatus.APPROVED);
        request.setId(41L);
        PromotionSummaryResponse summary = PromotionSummaryResponse.builder()
                .id(41L)
                .status(PromotionStatus.APPROVED)
                .build();
        when(requestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    assertThat(pageable.getPageNumber()).isZero();
                    assertThat(pageable.getPageSize()).isEqualTo(100);
                    assertThat(pageable.getSort().getOrderFor("requestedAt")).isNotNull();
                    return new PageImpl<>(List.of(request), pageable, 1);
                });
        when(mapper.toSummary(request)).thenReturn(summary);

        PromotionReportDetailPageResponse response = service.details(
                PromotionStatus.APPROVED,
                12L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28),
                -5,
                500);

        assertThat(response.getContent()).containsExactly(summary);
        assertThat(response.getPageNumber()).isZero();
        assertThat(response.getPageSize()).isEqualTo(100);
        assertThat(response.getTotalElements()).isEqualTo(1);
        verify(authorizationService).requireHrFinanceOrAdmin();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportDetailSpecificationFiltersStatusDepartmentEffectiveDateAndApprovalDate() {
        when(requestRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Specification<PromotionRequest> specification = invocation.getArgument(0);
                    Assertions.assertNotNull(specification);
                    return new PageImpl<>(List.of(), invocation.getArgument(1), 0);
                });

        PromotionReportDetailPageResponse response = service.details(
                PromotionStatus.IMPLEMENTED,
                22L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28),
                1,
                10);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPageNumber()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(10);
    }

    private long count(List<PromotionStatusReportResponse> response, PromotionStatus status) {
        return response.stream()
                .filter(row -> row.getStatus() == status)
                .findFirst()
                .orElseThrow()
                .getCount();
    }

    private PromotionRequest request(PromotionStatus status) {
        PromotionRequest request = new PromotionRequest();
        request.setStatus(status);
        return request;
    }

    private PromotionRequest request(PromotionStatus status, Department department, String monthly,
                                     String annual, String increasePercent) {
        PromotionRequest request = request(status);
        request.setProposedDepartment(department);
        request.setMonthlyPayrollImpact(new BigDecimal(monthly));
        request.setAnnualPayrollImpact(new BigDecimal(annual));
        request.setSalaryIncreasePercent(new BigDecimal(increasePercent));
        return request;
    }

    private Department department(Long id, String name) {
        Department department = new Department();
        department.setId(id);
        department.setName(name);
        return department;
    }
}
