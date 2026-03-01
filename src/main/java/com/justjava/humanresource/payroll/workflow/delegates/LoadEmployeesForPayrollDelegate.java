package com.justjava.humanresource.payroll.workflow.delegates;


import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Component("loadEmployeesForPayrollDelegate")
@RequiredArgsConstructor
public class LoadEmployeesForPayrollDelegate implements JavaDelegate {

    private final EmployeeService employeeService;

    @Override
    public void execute(DelegateExecution execution) {

/*        Long companyId = (Long) execution.getVariable("companyId");
        LocalDate periodStart = (LocalDate) execution.getVariable("periodStart");
        LocalDate periodEnd = (LocalDate) execution.getVariable("periodEnd");*/

        List<EmployeeDTO> activeEmployees =
                employeeService.getAllEmployees();

        List<Long> employeeIds = activeEmployees.stream()
                .map(EmployeeDTO::getId)
                .collect(Collectors.toList());

        execution.setVariable("employeeIds", employeeIds);
    }
}