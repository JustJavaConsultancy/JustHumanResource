package com.justjava.humanresource.request.controller;

import com.justjava.humanresource.core.config.AuthenticationManager;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowRequestPageControllerTest {

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final WorkflowRequestPageController controller = new WorkflowRequestPageController(authenticationManager);

    @Test
    void employeeOnlyUserIsRedirectedFromSharedRequestsPage() {
        when(authenticationManager.isEmployee()).thenReturn(true);

        String view = controller.requests(new ConcurrentModel());

        assertEquals("redirect:/employee/requests", view);
    }

    @Test
    void hrUserCanOpenSharedRequestsPage() {
        when(authenticationManager.isHumanResource()).thenReturn(true);

        ConcurrentModel model = new ConcurrentModel();
        String view = controller.requests(model);

        assertEquals("request/main", view);
        assertEquals("Requests", model.getAttribute("title"));
    }

    @Test
    void employeeRoutesUseEmployeeTemplates() {
        assertEquals("request/employee-main", controller.employeeRequests(new ConcurrentModel()));
        assertEquals("request/employee-detail", controller.employeeRequestDetail(new ConcurrentModel()));
        assertEquals("request/employee-userGuide", controller.employeeUserGuide(new ConcurrentModel()));
    }
}
