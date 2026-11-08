package com.justjava.humanresource.finance;

import com.justjava.humanresource.payroll.dto.PayrollLockChangeReportDTO;
import com.justjava.humanresource.payroll.service.PayrollLockChangeService;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;
import com.justjava.humanresource.workflow.service.FlowableTaskService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;



@ExtendWith(MockitoExtension.class)
class FinanceControllerCloseErrorTest {

    @Mock
    private FlowableTaskService flowableTaskService;

    @Mock
    private PayrollLockChangeService payrollLockChangeService;

    @InjectMocks
    private FinanceController financeController;

    @Test
    void displaysAccountingPreflightFailureOnFinanceLockApprovalPage() {
        String taskId = "finance-task";
        String message = "Cannot close for accounting: journal amounts cannot be negative.";
        when(flowableTaskService.getActiveTaskById(taskId))
                .thenReturn(FlowableTaskDTO.builder().taskId(taskId).build());
        when(payrollLockChangeService.buildReport(org.mockito.ArgumentMatchers.any()))
                .thenReturn(PayrollLockChangeReportDTO.builder().hasChanges(false).build());
        doThrow(new IllegalStateException(message))
                .when(flowableTaskService).completeTask(taskId, java.util.Map.of("approved", true));
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        assertThat(financeController.approveLock(taskId, flash))
                .isEqualTo("redirect:/finance/lockApproval");
        assertThat(flash.getFlashAttributes().get("lockApprovalBlockedMessage")).isEqualTo(message);
    }
}
