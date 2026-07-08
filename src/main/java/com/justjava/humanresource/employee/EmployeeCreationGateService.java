package com.justjava.humanresource.employee;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.service.PayrollPeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmployeeCreationGateService {

    public static final String BLOCKED_REASON =
            "Employee creation and bulk upload are disabled because no payroll period is currently open. " +
                    "Please open a payroll period before adding employees.";

    private final PayrollPeriodService payrollPeriodService;

    public boolean canCreateEmployees(Long companyId) {
        PayrollPeriod openPeriod = payrollPeriodService.getOpenPeriod(companyId);
        return openPeriod != null && openPeriod.getStatus() == PayrollPeriodStatus.OPEN;
    }

    public String getBlockedReason(Long companyId) {
        return canCreateEmployees(companyId) ? null : BLOCKED_REASON;
    }

    public void assertCanCreateEmployees(Long companyId) {
        if (!canCreateEmployees(companyId)) {
            throw new EmployeeCreationBlockedException(BLOCKED_REASON);
        }
    }
}
