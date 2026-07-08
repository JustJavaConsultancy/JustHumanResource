package com.justjava.humanresource.employee;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeCreationGateServiceTest {

    private final PayrollPeriodService payrollPeriodService = mock(PayrollPeriodService.class);
    private final EmployeeCreationGateService service = new EmployeeCreationGateService(payrollPeriodService);

    @Test
    void allowsEmployeeCreationWhenOpenPeriodExists() {
        PayrollPeriod period = new PayrollPeriod();
        period.setStatus(PayrollPeriodStatus.OPEN);
        when(payrollPeriodService.getOpenPeriod(1L)).thenReturn(period);

        assertTrue(service.canCreateEmployees(1L));
        assertNull(service.getBlockedReason(1L));
    }

    @Test
    void blocksEmployeeCreationWhenNoOpenPeriodExists() {
        when(payrollPeriodService.getOpenPeriod(1L)).thenReturn(null);

        assertFalse(service.canCreateEmployees(1L));
        assertThrows(EmployeeCreationBlockedException.class, () -> service.assertCanCreateEmployees(1L));
    }
}
