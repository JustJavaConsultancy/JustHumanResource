package com.justjava.humanresource.request.grouped;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.request.entity.WorkflowRequest;
import com.justjava.humanresource.request.enums.RequestStatus;
import com.justjava.humanresource.request.repository.WorkflowRequestRepository;
import com.justjava.humanresource.utils.EmailService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupedApprovalNoticeDispatcherTest {
    @Mock GroupedApprovalNoticeRepository notices;
    @Mock GroupedApprovalAssignmentRepository assignments;
    @Mock GroupedApprovalCycleRepository cycles;
    @Mock WorkflowRequestRepository requests;
    @Mock EmailService email;
    @Mock EmployeeRepository employees;
    @InjectMocks GroupedApprovalNoticeDispatcher dispatcher;
    GroupedApprovalNotice notice;
    @BeforeEach void setup() {
        notice = new GroupedApprovalNotice(); notice.setId(1L); notice.setRequestId(2L); notice.setCycleId(3L); notice.setKind("APPROVED");
        when(notices.findTop20ByDeliveredAtIsNullAndNextAttemptAtBeforeOrderById(any())).thenReturn(List.of(notice));
        WorkflowRequest request = new WorkflowRequest(); request.setRequesterEmployeeId(4L); request.setRequestNumber("REQ-2"); request.setStatus(RequestStatus.APPROVED);
        when(requests.findById(2L)).thenReturn(Optional.of(request));
        GroupedApprovalCycle cycle = new GroupedApprovalCycle(); cycle.setState("APPROVED");
        when(cycles.findById(3L)).thenReturn(Optional.of(cycle));
    }
    @Test void deliveryFailureRemainsRetryableInsteadOfBeingReportedAsSent() {
        Employee employee = new Employee(); employee.setEmail("recipient@example.test"); when(employees.findById(4L)).thenReturn(Optional.of(employee));
        when(email.sendEmail(anyString(),anyString(),anyString(),anyString())).thenThrow(new IllegalStateException("Transport unavailable"));
        dispatcher.dispatch();
        assertThat(notice.getDeliveredAt()).isNull(); assertThat(notice.getAttempts()).isEqualTo(1);
        assertThat(notice.getLastError()).isEqualTo("IllegalStateException");
        verify(notices).save(notice);
    }
    @Test void supersededCycleSuppressesObsoleteMessages() {
        GroupedApprovalCycle cycle = new GroupedApprovalCycle(); cycle.setState("SUPERSEDED"); when(cycles.findById(3L)).thenReturn(Optional.of(cycle));
        dispatcher.dispatch();
        verifyNoInteractions(email,employees); assertThat(notice.getDeliveredAt()).isNotNull();
    }
}
