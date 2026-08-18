package com.justjava.humanresource.employee;

import com.justjava.humanresource.hr.service.EmployeeUploadService;
import com.justjava.humanresource.hr.service.JobHrEmployeeAccessService;
import com.justjava.humanresource.onboarding.dto.StartEmployeeOnboardingCommand;
import com.justjava.humanresource.onboarding.service.EmployeeOnboardingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EmployeeControllerPayrollPeriodGateTest {

    private final EmployeeCreationGateService employeeCreationGateService = mock(EmployeeCreationGateService.class);
    private final EmployeeOnboardingService employeeOnboardingService = mock(EmployeeOnboardingService.class);
    private final EmployeeUploadService employeeUploadService = mock(EmployeeUploadService.class);
    private final JobHrEmployeeAccessService jobHrEmployeeAccessService = mock(JobHrEmployeeAccessService.class);
    private final EmployeeController controller = new EmployeeController();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "employeeCreationGateService", employeeCreationGateService);
        ReflectionTestUtils.setField(controller, "employeeOnboardingService", employeeOnboardingService);
        ReflectionTestUtils.setField(controller, "employeeUploadService", employeeUploadService);
        ReflectionTestUtils.setField(controller, "jobHrEmployeeAccessService", jobHrEmployeeAccessService);
    }

    @Test
    void startOnboardingReturnsConflictWhenPayrollPeriodIsNotOpen() {
        doThrow(new EmployeeCreationBlockedException(EmployeeCreationGateService.BLOCKED_REASON))
                .when(employeeCreationGateService).assertCanCreateEmployees(1L);

        StartEmployeeOnboardingCommand command = new StartEmployeeOnboardingCommand();
        command.setJobStepId(10L);

        Object response = controller.startOnboarding(command, "humanResource", null, null, null);

        ResponseEntity<?> entity = assertInstanceOf(ResponseEntity.class, response);
        assertEquals(HttpStatus.CONFLICT, entity.getStatusCode());
        assertEquals("PAYROLL_PERIOD_NOT_OPEN", ((Map<?, ?>) entity.getBody()).get("error"));
        verify(employeeOnboardingService, never()).startOnboarding(command, "humanResource");
        verify(jobHrEmployeeAccessService, never()).assertCanUseJobStep(10L);
    }

    @Test
    void uploadCsvReturnsConflictWhenPayrollPeriodIsNotOpen() {
        doThrow(new EmployeeCreationBlockedException(EmployeeCreationGateService.BLOCKED_REASON))
                .when(employeeCreationGateService).assertCanCreateEmployees(1L);
        MockMultipartFile file = new MockMultipartFile("file", "employees.csv", "text/csv", "email\njane@example.com".getBytes());

        ResponseEntity<?> response = controller.uploadCsv(file);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("PAYROLL_PERIOD_NOT_OPEN", ((Map<?, ?>) response.getBody()).get("error"));
        verify(employeeUploadService, never()).uploadEmployees(file);
    }
}
