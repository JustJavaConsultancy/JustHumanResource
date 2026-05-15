package com.justjava.humanresource.payroll.workflow.delegates;


import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component("loadEmployeesForPayrollDelegate")
@RequiredArgsConstructor
public class LoadEmployeesForPayrollDelegate implements JavaDelegate {

    private final EmployeeService employeeService;

    @Override
    public void execute(DelegateExecution execution) {

        java.time.LocalDate payrollDate = java.time.LocalDate.now();
        Object payrollDateVar = execution.getVariable("payrollDate");
        if (payrollDateVar instanceof java.time.LocalDate date) {
            payrollDate = date;
        }

        List<EmployeeDTO> activeEmployees =
                employeeService.getPayrollEligibleEmployees(payrollDate).stream()
                        .map(employee -> {
                            EmployeeDTO dto = new EmployeeDTO();
                            dto.setId(employee.getId());
                            return dto;
                        })
                        .toList();

        List<Long> employeeIds = activeEmployees.stream()
                .map(EmployeeDTO::getId)
                .collect(Collectors.toList());

        execution.setVariable("employeeIds", employeeIds);
    }
}
