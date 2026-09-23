package com.justjava.humanresource.kpi.service;

import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.kpi.dto.KpiReportFilterDTO;
import com.justjava.humanresource.kpi.dto.KpiScorecardMonitoringDTO;
import com.justjava.humanresource.kpi.entity.AppraisalCycle;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.entity.KpiScorecardTemplate;
import com.justjava.humanresource.kpi.entity.KpiTemplateAssignment;
import com.justjava.humanresource.kpi.enums.AppraisalStatus;
import com.justjava.humanresource.kpi.enums.KpiScorecardTemplateStatus;
import com.justjava.humanresource.kpi.repositories.AppraisalCycleRepository;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.kpi.repositories.KpiAppraisalLineRepository;
import com.justjava.humanresource.kpi.repositories.KpiScorecardTemplateItemRepository;
import com.justjava.humanresource.kpi.repositories.KpiScorecardTemplateRepository;
import com.justjava.humanresource.kpi.repositories.KpiScorecardTemplateVersionRepository;
import com.justjava.humanresource.kpi.repositories.KpiTemplateAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class KpiReportingServiceTest {

    @Mock private KpiScorecardTemplateRepository templateRepository;
    @Mock private KpiScorecardTemplateItemRepository templateItemRepository;
    @Mock private KpiScorecardTemplateVersionRepository versionRepository;
    @Mock private KpiTemplateAssignmentRepository assignmentRepository;
    @Mock private EmployeeAppraisalRepository appraisalRepository;
    @Mock private KpiAppraisalLineRepository lineRepository;
    @Mock private AppraisalCycleRepository cycleRepository;

    private KpiReportingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new KpiReportingService(
                templateRepository,
                templateItemRepository,
                versionRepository,
                assignmentRepository,
                appraisalRepository,
                lineRepository,
                cycleRepository
        );
    }

    @Test
    void monitoringCountsTemplateStatusesAssignmentsAndReviewQueues() {
        KpiScorecardTemplate draft = template(1L, "Draft", KpiScorecardTemplateStatus.DRAFT);
        KpiScorecardTemplate published = template(2L, "Published", KpiScorecardTemplateStatus.PUBLISHED);
        Department department = new Department();
        department.setId(10L);
        KpiTemplateAssignment assignment = KpiTemplateAssignment.builder()
                .template(published)
                .department(department)
                .active(true)
                .build();
        EmployeeAppraisal selfPending = EmployeeAppraisal.builder().status(AppraisalStatus.DRAFT).build();
        EmployeeAppraisal managerPending = EmployeeAppraisal.builder().status(AppraisalStatus.SELF_REVIEW_SUBMITTED).build();

        when(templateRepository.findAll()).thenReturn(List.of(draft, published));
        when(assignmentRepository.findAll()).thenReturn(List.of(assignment));
        when(appraisalRepository.findAll()).thenReturn(List.of(selfPending, managerPending));
        when(templateItemRepository.findByTemplate_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());
        when(templateItemRepository.findByTemplate_IdOrderBySortOrderAscIdAsc(2L)).thenReturn(List.of());
        when(versionRepository.findAll()).thenReturn(List.of());

        KpiScorecardMonitoringDTO dashboard = service.getMonitoringDashboard();

        assertThat(dashboard.getDraftTemplates()).isEqualTo(1);
        assertThat(dashboard.getPublishedTemplates()).isEqualTo(1);
        assertThat(dashboard.getActiveDepartmentAssignments()).isEqualTo(1);
        assertThat(dashboard.getPendingSelfReview()).isEqualTo(1);
        assertThat(dashboard.getPendingManagerReview()).isEqualTo(1);
        assertThat(dashboard.getTemplatesWithValidationIssues()).hasSize(2);
    }

    @Test
    void performanceReportAppliesCycleFilterAndAveragesScores() {
        AppraisalCycle cycle = AppraisalCycle.builder().id(5L).name("2026 Q1").year(2026).quarter(1).build();
        Employee employee = new Employee();
        employee.setId(20L);
        employee.setFirstName("Ada");
        employee.setLastName("Lovelace");
        EmployeeAppraisal included = EmployeeAppraisal.builder()
                .id(30L)
                .cycle(cycle)
                .employee(employee)
                .finalScore(BigDecimal.valueOf(80))
                .status(AppraisalStatus.FINALIZED)
                .build();
        EmployeeAppraisal excluded = EmployeeAppraisal.builder()
                .id(31L)
                .cycle(AppraisalCycle.builder().id(6L).name("2026 Q2").build())
                .finalScore(BigDecimal.valueOf(40))
                .build();
        KpiReportFilterDTO filter = new KpiReportFilterDTO();
        filter.setCycleId(5L);

        when(appraisalRepository.findAll()).thenReturn(List.of(included, excluded));
        when(cycleRepository.findById(5L)).thenReturn(Optional.of(cycle));
        when(cycleRepository.findAll()).thenReturn(List.of(cycle));

        var report = service.getPerformanceReport(filter);

        assertThat(report.getCycleName()).isEqualTo("2026 Q1");
        assertThat(report.getAverageScore()).isEqualByComparingTo("80.00");
        assertThat(report.getTotalAppraisals()).isEqualTo(1);
        assertThat(report.getCompletionRate()).isEqualByComparingTo("100.00");
        assertThat(report.getTopPerformers()).extracting("employeeName").containsExactly("Ada Lovelace");
    }

    @Test
    void exportsTemplatesAsCsv() {
        when(templateRepository.findAll()).thenReturn(List.of(template(1L, "Head HR", KpiScorecardTemplateStatus.PUBLISHED)));

        String csv = new String(service.export("templates", "csv", new KpiReportFilterDTO()));

        assertThat(csv).contains("id,name,role,status,totalWeight,source,importedBy");
        assertThat(csv).contains("Head HR");
    }

    private KpiScorecardTemplate template(Long id, String name, KpiScorecardTemplateStatus status) {
        KpiScorecardTemplate template = KpiScorecardTemplate.builder()
                .name(name)
                .status(status)
                .totalWeight(BigDecimal.ONE)
                .build();
        template.setId(id);
        return template;
    }
}
