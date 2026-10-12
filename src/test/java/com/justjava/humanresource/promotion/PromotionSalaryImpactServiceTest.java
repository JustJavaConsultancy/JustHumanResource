package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.kpi.repositories.EmployeeAppraisalRepository;
import com.justjava.humanresource.promotion.dto.PromotionRequestCommand;
import com.justjava.humanresource.promotion.dto.PromotionSalaryImpactResponse;
import com.justjava.humanresource.promotion.repository.PromotionApprovalStepRepository;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import com.justjava.humanresource.promotion.service.PromotionActivityService;
import com.justjava.humanresource.promotion.service.PromotionApprovalRouteService;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionCurrentUserService;
import com.justjava.humanresource.promotion.service.PromotionEligibilityService;
import com.justjava.humanresource.promotion.service.PromotionImplementationService;
import com.justjava.humanresource.promotion.service.PromotionMapper;
import com.justjava.humanresource.promotion.service.PromotionNotificationService;
import com.justjava.humanresource.promotion.service.PromotionRequiredDocumentService;
import com.justjava.humanresource.promotion.service.PromotionNumberService;
import com.justjava.humanresource.promotion.service.PromotionServiceImpl;
import com.justjava.humanresource.promotion.service.PromotionWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionSalaryImpactServiceTest {

    @Mock PromotionRequestRepository requestRepository;
    @Mock PromotionApprovalStepRepository approvalStepRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock JobStepRepository jobStepRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock EmployeeAppraisalRepository appraisalRepository;
    @Mock PromotionNumberService numberService;
    @Mock PromotionEligibilityService eligibilityService;
    @Mock PromotionImplementationService implementationService;
    @Mock PromotionActivityService activityService;
    @Mock PromotionMapper mapper;
    @Mock PromotionCurrentUserService currentUserService;
    @Mock PromotionWorkflowService workflowService;
    @Mock PromotionApprovalRouteService approvalRouteService;
    @Mock PromotionAuthorizationService authorizationService;
    @Mock PromotionNotificationService notificationService;
    @Mock PromotionRequiredDocumentService requiredDocumentService;

    private PromotionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromotionServiceImpl(
                requestRepository,
                approvalStepRepository,
                employeeRepository,
                jobStepRepository,
                departmentRepository,
                appraisalRepository,
                numberService,
                eligibilityService,
                implementationService,
                activityService,
                mapper,
                currentUserService,
                workflowService,
                approvalRouteService,
                authorizationService,
                notificationService,
                requiredDocumentService
        );
    }

    @Test
    void calculatesSalaryImpactFromProposedRoleSalary() {
        JobStep currentRole = role(10L, "Analyst", "100000.00");
        JobStep proposedRole = role(20L, "Senior Analyst", "125000.00");
        Employee employee = employee(currentRole);
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(jobStepRepository.findById(proposedRole.getId())).thenReturn(Optional.of(proposedRole));

        PromotionSalaryImpactResponse response = service.salaryImpact(command(employee.getId(), proposedRole.getId(), null));

        assertThat(response.getCurrentSalary()).isEqualByComparingTo("100000.00");
        assertThat(response.getProposedSalary()).isEqualByComparingTo("125000.00");
        assertThat(response.getSalaryIncreaseAmount()).isEqualByComparingTo("25000.00");
        assertThat(response.getSalaryIncreasePercent()).isEqualByComparingTo("25.00");
        assertThat(response.getMonthlyPayrollImpact()).isEqualByComparingTo("25000.00");
        assertThat(response.getAnnualPayrollImpact()).isEqualByComparingTo("300000.00");
        verify(authorizationService).requireHrFinanceOrAdmin();
    }

    @Test
    void usesExplicitProposedSalaryWhenProvided() {
        JobStep currentRole = role(10L, "Analyst", "100000.00");
        JobStep proposedRole = role(20L, "Senior Analyst", "125000.00");
        Employee employee = employee(currentRole);
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(jobStepRepository.findById(proposedRole.getId())).thenReturn(Optional.of(proposedRole));

        PromotionSalaryImpactResponse response = service.salaryImpact(
                command(employee.getId(), proposedRole.getId(), new BigDecimal("130000.00")));

        assertThat(response.getProposedSalary()).isEqualByComparingTo("130000.00");
        assertThat(response.getSalaryIncreaseAmount()).isEqualByComparingTo("30000.00");
        assertThat(response.getSalaryIncreasePercent()).isEqualByComparingTo("30.00");
    }

    @Test
    void handlesEmployeeWithoutCurrentSalary() {
        JobStep proposedRole = role(20L, "Analyst", "100000.00");
        Employee employee = employee(null);
        when(employeeRepository.findById(employee.getId())).thenReturn(Optional.of(employee));
        when(jobStepRepository.findById(proposedRole.getId())).thenReturn(Optional.of(proposedRole));

        PromotionSalaryImpactResponse response = service.salaryImpact(command(employee.getId(), proposedRole.getId(), null));

        assertThat(response.getCurrentSalary()).isEqualByComparingTo("0.00");
        assertThat(response.getSalaryIncreaseAmount()).isEqualByComparingTo("100000.00");
        assertThat(response.getSalaryIncreasePercent()).isEqualByComparingTo("0.00");
    }

    private PromotionRequestCommand command(Long employeeId, Long proposedRoleId, BigDecimal proposedSalary) {
        PromotionRequestCommand command = new PromotionRequestCommand();
        command.setEmployeeId(employeeId);
        command.setProposedRoleId(proposedRoleId);
        command.setEffectiveDate(LocalDate.now().plusDays(1));
        command.setJustification("Salary impact preview");
        command.setProposedSalary(proposedSalary);
        return command;
    }

    private Employee employee(JobStep currentRole) {
        Employee employee = new Employee();
        employee.setId(100L);
        employee.setFirstName("First");
        employee.setLastName("Last");
        employee.setJobStep(currentRole);
        return employee;
    }

    private JobStep role(Long id, String name, String salary) {
        JobStep role = new JobStep();
        role.setId(id);
        role.setName(name);
        role.setGrossSalary(new BigDecimal(salary));
        return role;
    }
}

