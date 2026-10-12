package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.orgStructure.entity.Company;
import com.justjava.humanresource.payroll.service.PayrollChangeOrchestrator;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import com.justjava.humanresource.promotion.entity.PromotionPayrollHandoff;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.dto.PromotionDecisionCommand;
import com.justjava.humanresource.promotion.dto.PromotionPayrollHandoffResponse;
import com.justjava.humanresource.promotion.enums.PromotionActivityType;
import com.justjava.humanresource.promotion.enums.PromotionPayrollHandoffStatus;
import com.justjava.humanresource.promotion.repository.PromotionPayrollHandoffRepository;
import com.justjava.humanresource.promotion.service.PromotionActivityService;
import com.justjava.humanresource.promotion.service.PromotionAuthorizationService;
import com.justjava.humanresource.promotion.service.PromotionCurrentUserService;
import com.justjava.humanresource.promotion.service.PromotionPayrollHandoffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionPayrollHandoffServiceTest {

    @Mock PromotionPayrollHandoffRepository handoffRepository;
    @Mock PromotionCurrentUserService currentUserService;
    @Mock PromotionActivityService activityService;
    @Mock PromotionAuthorizationService authorizationService;
    @Mock PayrollPeriodService payrollPeriodService;
    @Mock PayrollChangeOrchestrator payrollChangeOrchestrator;

    private PromotionPayrollHandoffService service;

    @BeforeEach
    void setUp() {
        service = new PromotionPayrollHandoffService(
                handoffRepository,
                currentUserService,
                activityService,
                authorizationService,
                payrollPeriodService,
                payrollChangeOrchestrator
        );
    }

    @Test
    void createsPendingPayrollHandoffForSalaryChange() {
        PromotionRequest request = request("100000.00", "125000.00");
        Employee actor = employee(7L);
        when(handoffRepository.existsByPromotionRequest_Id(request.getId())).thenReturn(false);
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(actor);

        service.createPendingHandoff(request);

        ArgumentCaptor<PromotionPayrollHandoff> captor = ArgumentCaptor.forClass(PromotionPayrollHandoff.class);
        verify(handoffRepository).save(captor.capture());
        PromotionPayrollHandoff handoff = captor.getValue();
        assertThat(handoff.getPromotionRequest()).isEqualTo(request);
        assertThat(handoff.getEmployee()).isEqualTo(request.getEmployee());
        assertThat(handoff.getPayGroup()).isEqualTo(request.getEmployee().getPayGroup());
        assertThat(handoff.getEffectiveDate()).isEqualTo(request.getEffectiveDate());
        assertThat(handoff.getProposedSalary()).isEqualByComparingTo("125000.00");
        assertThat(handoff.getCreatedBy()).isEqualTo(actor);
        verify(activityService).record(request, PromotionActivityType.PAYROLL_HANDOFF_CREATED, actor,
                "Payroll handoff created for salary change effective " + request.getEffectiveDate() + ".");
    }

    @Test
    void doesNotCreateDuplicatePayrollHandoff() {
        PromotionRequest request = request("100000.00", "125000.00");
        when(handoffRepository.existsByPromotionRequest_Id(request.getId())).thenReturn(true);

        service.createPendingHandoff(request);

        verify(handoffRepository, never()).save(any());
        verify(activityService, never()).record(any(), any(), any(), any());
    }

    @Test
    void skipsPayrollHandoffWhenSalaryDidNotChange() {
        PromotionRequest request = request("100000.00", "100000.00");
        request.setMonthlyPayrollImpact(BigDecimal.ZERO);
        request.setAnnualPayrollImpact(BigDecimal.ZERO);

        service.createPendingHandoff(request);

        verify(handoffRepository, never()).existsByPromotionRequest_Id(request.getId());
        verify(handoffRepository, never()).save(any());
    }

    @Test
    void listsPayrollHandoffsForHrFinanceOrAdmin() {
        PromotionPayrollHandoff handoff = new PromotionPayrollHandoff();
        handoff.setId(40L);
        handoff.setPromotionRequest(request("100000.00", "125000.00"));
        handoff.setEmployee(handoff.getPromotionRequest().getEmployee());
        handoff.setPayGroup(handoff.getEmployee().getPayGroup());
        handoff.setEffectiveDate(handoff.getPromotionRequest().getEffectiveDate());
        handoff.setCurrentSalary(handoff.getPromotionRequest().getCurrentSalary());
        handoff.setProposedSalary(handoff.getPromotionRequest().getProposedSalary());
        handoff.setMonthlyPayrollImpact(handoff.getPromotionRequest().getMonthlyPayrollImpact());
        handoff.setAnnualPayrollImpact(handoff.getPromotionRequest().getAnnualPayrollImpact());
        when(handoffRepository.findByStatusOrderByEffectiveDateAscIdAsc(PromotionPayrollHandoffStatus.PENDING))
                .thenReturn(List.of(handoff));

        List<PromotionPayrollHandoffResponse> response = service.list(PromotionPayrollHandoffStatus.PENDING);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getId()).isEqualTo(40L);
        assertThat(response.getFirst().getPayGroupName()).isEqualTo("Monthly");
        verify(authorizationService).requireHrFinanceOrAdmin();
    }

    @Test
    void acknowledgesPendingPayrollHandoff() {
        PromotionPayrollHandoff handoff = handoff(PromotionPayrollHandoffStatus.PENDING);
        Employee actor = employee(9L);
        when(handoffRepository.findById(handoff.getId())).thenReturn(java.util.Optional.of(handoff));
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(actor);
        when(handoffRepository.save(handoff)).thenReturn(handoff);

        PromotionPayrollHandoffResponse response = service.acknowledge(handoff.getId(), command("Reviewed by payroll"));

        assertThat(response.getStatus()).isEqualTo(PromotionPayrollHandoffStatus.ACKNOWLEDGED);
        assertThat(handoff.getAcknowledgedAt()).isNotNull();
        assertThat(handoff.getNote()).contains("Reviewed by payroll");
        verify(activityService).record(handoff.getPromotionRequest(),
                PromotionActivityType.PAYROLL_HANDOFF_ACKNOWLEDGED, actor, "Payroll handoff acknowledged.");
    }

    @Test
    void appliesAcknowledgedPayrollHandoff() {
        PromotionPayrollHandoff handoff = handoff(PromotionPayrollHandoffStatus.ACKNOWLEDGED);
        Employee actor = employee(9L);
        when(handoffRepository.findById(handoff.getId())).thenReturn(java.util.Optional.of(handoff));
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(actor);
        when(handoffRepository.save(handoff)).thenReturn(handoff);

        PromotionPayrollHandoffResponse response = service.apply(handoff.getId(), command("Applied in payroll"));

        assertThat(response.getStatus()).isEqualTo(PromotionPayrollHandoffStatus.APPLIED);
        assertThat(response.isExternalApplicationOnly()).isFalse();
        assertThat(response.getAppliedByName()).isEqualTo("First Last");
        assertThat(handoff.getAppliedAt()).isNotNull();
        assertThat(handoff.getAppliedBy()).isEqualTo(actor);
        assertThat(handoff.getNote()).contains("Applied in payroll");
        verify(payrollPeriodService).validatePayrollDate(1L, handoff.getEffectiveDate());
        verify(payrollChangeOrchestrator).recalculateForEmployee(handoff.getEmployee().getId(), handoff.getEffectiveDate());
        verify(activityService).record(handoff.getPromotionRequest(),
                PromotionActivityType.PAYROLL_HANDOFF_APPLIED, actor, "Payroll handoff applied to payroll.");
    }

    @Test
    void returnsAlreadyAppliedPayrollHandoffWithoutReapplyingPayroll() {
        PromotionPayrollHandoff handoff = handoff(PromotionPayrollHandoffStatus.APPLIED);
        when(handoffRepository.findById(handoff.getId())).thenReturn(java.util.Optional.of(handoff));

        PromotionPayrollHandoffResponse response = service.apply(handoff.getId(), command("Retry"));

        assertThat(response.getStatus()).isEqualTo(PromotionPayrollHandoffStatus.APPLIED);
        verify(payrollPeriodService, never()).validatePayrollDate(any(), any());
        verify(payrollChangeOrchestrator, never()).recalculateForEmployee(any(), any());
        verify(handoffRepository, never()).save(any());
    }

    @Test
    void rejectsApplyWhenEffectiveDateIsOutsideOpenPayrollPeriod() {
        PromotionPayrollHandoff handoff = handoff(PromotionPayrollHandoffStatus.ACKNOWLEDGED);
        when(handoffRepository.findById(handoff.getId())).thenReturn(java.util.Optional.of(handoff));
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(employee(9L));
        doThrow(new IllegalStateException("Payroll date 2026-11-01 is outside the OPEN payroll period."))
                .when(payrollPeriodService).validatePayrollDate(1L, handoff.getEffectiveDate());

        assertThatThrownBy(() -> service.apply(handoff.getId(), command("Applied in payroll")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payroll date 2026-11-01 is outside the OPEN payroll period.");

        assertThat(handoff.getStatus()).isEqualTo(PromotionPayrollHandoffStatus.ACKNOWLEDGED);
        assertThat(handoff.getAppliedAt()).isNull();
        assertThat(handoff.getAppliedBy()).isNull();
        verify(payrollChangeOrchestrator, never()).recalculateForEmployee(any(), any());
        verify(handoffRepository, never()).save(any());
        verify(activityService, never()).record(any(), any(), any(), any());
    }

    @Test
    void rejectsApplyWhenEmployeeSalaryNoLongerMatchesHandoff() {
        PromotionPayrollHandoff handoff = handoff(PromotionPayrollHandoffStatus.ACKNOWLEDGED);
        handoff.getEmployee().getJobStep().setGrossSalary(new BigDecimal("130000.00"));
        when(handoffRepository.findById(handoff.getId())).thenReturn(java.util.Optional.of(handoff));
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(employee(9L));

        assertThatThrownBy(() -> service.apply(handoff.getId(), command("Applied in payroll")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Employee current job-step salary does not match the promotion handoff proposed salary.");

        verify(payrollPeriodService).validatePayrollDate(1L, handoff.getEffectiveDate());
        verify(payrollChangeOrchestrator, never()).recalculateForEmployee(any(), any());
        verify(handoffRepository, never()).save(any());
    }

    @Test
    void rejectsApplyWhenPayrollCompanyCannotBeResolved() {
        PromotionPayrollHandoff handoff = handoff(PromotionPayrollHandoffStatus.ACKNOWLEDGED);
        handoff.getEmployee().setDepartment(null);
        when(handoffRepository.findById(handoff.getId())).thenReturn(java.util.Optional.of(handoff));
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(employee(9L));

        assertThatThrownBy(() -> service.apply(handoff.getId(), command("Applied in payroll")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Payroll company could not be resolved for this promotion handoff.");

        verify(payrollPeriodService, never()).validatePayrollDate(any(), any());
        verify(handoffRepository, never()).save(any());
    }

    @Test
    void cancelsAcknowledgedPayrollHandoff() {
        PromotionPayrollHandoff handoff = handoff(PromotionPayrollHandoffStatus.ACKNOWLEDGED);
        Employee actor = employee(9L);
        when(handoffRepository.findById(handoff.getId())).thenReturn(java.util.Optional.of(handoff));
        when(currentUserService.getCurrentEmployeeOrSystem()).thenReturn(actor);
        when(handoffRepository.save(handoff)).thenReturn(handoff);

        PromotionPayrollHandoffResponse response = service.cancel(handoff.getId(), command("Handled manually"));

        assertThat(response.getStatus()).isEqualTo(PromotionPayrollHandoffStatus.CANCELLED);
        assertThat(handoff.getNote()).contains("Handled manually");
        verify(activityService).record(handoff.getPromotionRequest(),
                PromotionActivityType.PAYROLL_HANDOFF_CANCELLED, actor, "Payroll handoff cancelled.");
    }

    @Test
    void rejectsAcknowledgeForAppliedPayrollHandoff() {
        PromotionPayrollHandoff handoff = handoff(PromotionPayrollHandoffStatus.APPLIED);
        when(handoffRepository.findById(handoff.getId())).thenReturn(java.util.Optional.of(handoff));

        assertThatThrownBy(() -> service.acknowledge(handoff.getId(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only pending payroll handoffs can be acknowledged.");

        verify(handoffRepository, never()).save(any());
    }

    private PromotionRequest request(String currentSalary, String proposedSalary) {
        PayGroup payGroup = new PayGroup();
        payGroup.setId(3L);
        payGroup.setName("Monthly");
        Employee employee = employee(5L);
        employee.setPayGroup(payGroup);
        employee.setDepartment(department());
        employee.setJobStep(jobStep(new BigDecimal(proposedSalary)));

        PromotionRequest request = new PromotionRequest();
        request.setId(20L);
        request.setRequestNumber("PROM-2026-0001");
        request.setEmployee(employee);
        request.setEffectiveDate(LocalDate.of(2026, 11, 1));
        request.setCurrentSalary(new BigDecimal(currentSalary));
        request.setProposedSalary(new BigDecimal(proposedSalary));
        request.setMonthlyPayrollImpact(new BigDecimal("25000.00"));
        request.setAnnualPayrollImpact(new BigDecimal("300000.00"));
        return request;
    }

    private PromotionPayrollHandoff handoff(PromotionPayrollHandoffStatus status) {
        PromotionPayrollHandoff handoff = new PromotionPayrollHandoff();
        handoff.setId(40L);
        handoff.setPromotionRequest(request("100000.00", "125000.00"));
        handoff.setEmployee(handoff.getPromotionRequest().getEmployee());
        handoff.setPayGroup(handoff.getEmployee().getPayGroup());
        handoff.setEffectiveDate(handoff.getPromotionRequest().getEffectiveDate());
        handoff.setCurrentSalary(handoff.getPromotionRequest().getCurrentSalary());
        handoff.setProposedSalary(handoff.getPromotionRequest().getProposedSalary());
        handoff.setMonthlyPayrollImpact(handoff.getPromotionRequest().getMonthlyPayrollImpact());
        handoff.setAnnualPayrollImpact(handoff.getPromotionRequest().getAnnualPayrollImpact());
        handoff.setStatus(status);
        handoff.setNote("Created from implemented promotion PROM-2026-0001.");
        return handoff;
    }

    private PromotionDecisionCommand command(String comment) {
        PromotionDecisionCommand command = new PromotionDecisionCommand();
        command.setComment(comment);
        return command;
    }

    private Employee employee(Long id) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setFirstName("First");
        employee.setLastName("Last");
        return employee;
    }

    private JobStep jobStep(BigDecimal grossSalary) {
        JobStep jobStep = new JobStep();
        jobStep.setId(4L);
        jobStep.setGrossSalary(grossSalary);
        return jobStep;
    }

    private Department department() {
        Company company = new Company();
        company.setId(1L);
        Department department = new Department();
        department.setId(2L);
        department.setCompany(company);
        return department;
    }
}
