package com.justjava.humanresource.leave;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.service.EmployeeService;
import com.justjava.humanresource.leave.service.LeaveWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaveControllerTest {

    private final LeaveWorkflowService leaveWorkflowService = mock(LeaveWorkflowService.class);
    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final LeaveController controller = new LeaveController(
            leaveWorkflowService,
            employeeService,
            authenticationManager
    );

    @Test
    void employeeOnlyUserIsRedirectedToEmployeeLeavePage() {
        when(authenticationManager.isEmployee()).thenReturn(true);

        String view = controller.leavePage(new ConcurrentModel());

        assertEquals("redirect:/employee/leave", view);
    }

    @Test
    void hrUserCanOpenLeaveManagementPage() {
        when(authenticationManager.isEmployee()).thenReturn(false);
        when(authenticationManager.isHumanResource()).thenReturn(true);
        when(employeeService.getAllEmployees()).thenReturn(List.of());

        ConcurrentModel model = new ConcurrentModel();
        String view = controller.leavePage(model);

        assertEquals("leave/main", view);
        assertEquals("Leave Management", model.getAttribute("title"));
        verify(employeeService).getAllEmployees();
    }
}
