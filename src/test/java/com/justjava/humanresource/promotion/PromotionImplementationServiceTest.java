package com.justjava.humanresource.promotion;

import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import com.justjava.humanresource.hr.entity.JobStep;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.hr.repository.EmployeePositionHistoryRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.promotion.dto.PromotionImplementationCommand;
import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import com.justjava.humanresource.promotion.repository.PromotionRequestRepository;
import com.justjava.humanresource.promotion.service.PromotionActivityService;
import com.justjava.humanresource.promotion.service.PromotionCurrentUserService;
import com.justjava.humanresource.promotion.service.PromotionImplementationService;
import com.justjava.humanresource.promotion.service.PromotionNotificationService;
import com.justjava.humanresource.promotion.service.PromotionPayrollHandoffService;
import com.justjava.humanresource.promotion.service.PromotionRequiredDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionImplementationServiceTest {

    @Mock PromotionRequestRepository requestRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock EmployeePositionHistoryRepository positionHistoryRepository;
    @Mock PromotionActivityService activityService;
    @Mock PromotionCurrentUserService currentUserService;
    @Mock PromotionNotificationService notificationService;
    @Mock PromotionPayrollHandoffService payrollHandoffService;
    @Mock PromotionRequiredDocumentService requiredDocumentService;

    private PromotionImplementationService service;

    @BeforeEach
    void setUp() {
        service = new PromotionImplementationService(
                requestRepository,
                employeeRepository,
                positionHistoryRepository,
                activityService,
                currentUserService,
                notificationService,
                payrollHandoffService,
                requiredDocumentService
        );
    }

    @Test
    void createsNewPositionHistoryAndMarksRequestImplemented() {
        PromotionRequest request = approvedRequest();
        EmployeePositionHistory current = EmployeePositionHistory.builder()
                .employee(request.getEmployee())
                .department(request.getCurrentDepartment())
                .jobStep(request.getCurrentRole())
                .payGroup(request.getEmployee().getPayGroup())
                .effectiveFrom(LocalDate.now().minusYears(1))
                .current(true)
                .build();
        when(positionHistoryRepository.findAllByEmployeeIdAndEffectiveFrom(
                request.getEmployee().getId(), request.getEffectiveDate())).thenReturn(List.of());
        when(positionHistoryRepository.findByEmployee_IdAndCurrentTrue(request.getEmployee().getId()))
                .thenReturn(Optional.of(current));

        service.implement(request, command("Implemented"));

        ArgumentCaptor<EmployeePositionHistory> historyCaptor = ArgumentCaptor.forClass(EmployeePositionHistory.class);
        verify(positionHistoryRepository, org.mockito.Mockito.times(2)).save(historyCaptor.capture());
        assertThat(current.isCurrent()).isFalse();
        assertThat(current.getEffectiveTo()).isEqualTo(request.getEffectiveDate().minusDays(1));
        EmployeePositionHistory newHistory = historyCaptor.getAllValues().get(1);
        assertThat(newHistory.getJobStep()).isEqualTo(request.getProposedRole());
        assertThat(newHistory.getDepartment()).isEqualTo(request.getProposedDepartment());
        assertThat(newHistory.isCurrent()).isTrue();
        assertThat(request.getStatus()).isEqualTo(PromotionStatus.IMPLEMENTED);
        assertThat(request.getImplementationComment()).isEqualTo("Implemented");
        verify(employeeRepository).save(request.getEmployee());
        verify(requestRepository).save(request);
        verify(payrollHandoffService).createPendingHandoff(request);
        verify(requiredDocumentService).assertRequiredDocumentsPresent(
                request, PromotionRequiredDocumentService.EnforcementStage.IMPLEMENTATION);
    }

    @Test
    void doesNotCreateDuplicateHistoryWhenPromotionPositionAlreadyExists() {
        PromotionRequest request = approvedRequest();
        EmployeePositionHistory existing = EmployeePositionHistory.builder()
                .employee(request.getEmployee())
                .department(request.getProposedDepartment())
                .jobStep(request.getProposedRole())
                .payGroup(request.getEmployee().getPayGroup())
                .effectiveFrom(request.getEffectiveDate())
                .current(true)
                .build();
        when(positionHistoryRepository.findAllByEmployeeIdAndEffectiveFrom(
                request.getEmployee().getId(), request.getEffectiveDate())).thenReturn(List.of(existing));

        service.implement(request, command("Retry"));

        verify(positionHistoryRepository, never()).findByEmployee_IdAndCurrentTrue(request.getEmployee().getId());
        verify(positionHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any(EmployeePositionHistory.class));
        assertThat(request.getStatus()).isEqualTo(PromotionStatus.IMPLEMENTED);
        assertThat(request.getEmployee().getJobStep()).isEqualTo(request.getProposedRole());
        assertThat(request.getEmployee().getDepartment()).isEqualTo(request.getProposedDepartment());
        verify(payrollHandoffService).createPendingHandoff(request);
    }

    @Test
    void rejectsImplementationWhenEmployeeRoleChangedSinceApproval() {
        PromotionRequest request = approvedRequest();
        JobStep changedRole = new JobStep();
        changedRole.setId(99L);
        request.getEmployee().setJobStep(changedRole);
        when(positionHistoryRepository.findAllByEmployeeIdAndEffectiveFrom(
                request.getEmployee().getId(), request.getEffectiveDate())).thenReturn(List.of());

        assertThatThrownBy(() -> service.implement(request, command("Conflict")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Employee current role no longer matches the promotion baseline.");

        verify(positionHistoryRepository, never()).save(org.mockito.ArgumentMatchers.any(EmployeePositionHistory.class));
        verify(requestRepository, never()).save(request);
    }

    @Test
    void rejectsImplementationWhenRequiredDocumentsAreMissing() {
        PromotionRequest request = approvedRequest();
        org.mockito.Mockito.doThrow(new IllegalStateException("Promotion request is missing required documents: [PROMOTION_LETTER]"))
                .when(requiredDocumentService)
                .assertRequiredDocumentsPresent(request, PromotionRequiredDocumentService.EnforcementStage.IMPLEMENTATION);

        assertThatThrownBy(() -> service.implement(request, command("Missing documents")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing required documents");

        verify(positionHistoryRepository, never()).findAllByEmployeeIdAndEffectiveFrom(
                request.getEmployee().getId(), request.getEffectiveDate());
        verify(employeeRepository, never()).save(request.getEmployee());
        verify(requestRepository, never()).save(request);
    }

    @Test
    void implementedRequestRetryReconcilesBaselineEmployeeAndPayrollHandoff() {
        PromotionRequest request = approvedRequest();
        request.setStatus(PromotionStatus.IMPLEMENTED);

        PromotionRequest result = service.implement(request, command("Retry"));

        assertThat(result).isEqualTo(request);
        assertThat(request.getEmployee().getJobStep()).isEqualTo(request.getProposedRole());
        assertThat(request.getEmployee().getDepartment()).isEqualTo(request.getProposedDepartment());
        verify(employeeRepository).save(request.getEmployee());
        verify(payrollHandoffService).createPendingHandoff(request);
        verify(requestRepository, never()).save(request);
        verify(activityService, never()).record(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(notificationService, never()).implemented(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void implementedRequestRetryDoesNotRevertEmployeeMovedAfterPromotion() {
        PromotionRequest request = approvedRequest();
        request.setStatus(PromotionStatus.IMPLEMENTED);
        JobStep laterRole = new JobStep();
        laterRole.setId(99L);
        Department laterDepartment = new Department();
        laterDepartment.setId(88L);
        request.getEmployee().setJobStep(laterRole);
        request.getEmployee().setDepartment(laterDepartment);

        service.implement(request, command("Retry"));

        assertThat(request.getEmployee().getJobStep()).isEqualTo(laterRole);
        assertThat(request.getEmployee().getDepartment()).isEqualTo(laterDepartment);
        verify(employeeRepository, never()).save(request.getEmployee());
        verify(payrollHandoffService).createPendingHandoff(request);
        verify(requestRepository, never()).save(request);
    }

    private PromotionImplementationCommand command(String comment) {
        PromotionImplementationCommand command = new PromotionImplementationCommand();
        command.setComment(comment);
        return command;
    }

    private PromotionRequest approvedRequest() {
        Department currentDepartment = new Department();
        currentDepartment.setId(1L);
        Department proposedDepartment = new Department();
        proposedDepartment.setId(2L);
        JobStep currentRole = new JobStep();
        currentRole.setId(10L);
        currentRole.setDepartment(currentDepartment);
        JobStep proposedRole = new JobStep();
        proposedRole.setId(20L);
        proposedRole.setDepartment(proposedDepartment);
        PayGroup payGroup = new PayGroup();
        payGroup.setId(30L);

        Employee employee = new Employee();
        employee.setId(100L);
        employee.setDepartment(currentDepartment);
        employee.setJobStep(currentRole);
        employee.setPayGroup(payGroup);

        PromotionRequest request = new PromotionRequest();
        request.setId(200L);
        request.setStatus(PromotionStatus.APPROVED);
        request.setEmployee(employee);
        request.setCurrentDepartment(currentDepartment);
        request.setProposedDepartment(proposedDepartment);
        request.setCurrentRole(currentRole);
        request.setProposedRole(proposedRole);
        request.setEffectiveDate(LocalDate.now().plusDays(1));
        request.setCurrentSalary(new BigDecimal("100000.00"));
        request.setProposedSalary(new BigDecimal("125000.00"));
        request.setMonthlyPayrollImpact(new BigDecimal("25000.00"));
        request.setAnnualPayrollImpact(new BigDecimal("300000.00"));
        return request;
    }
}

