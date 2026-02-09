package com.justjava.humanresource.hr.service.impl;


import com.justjava.humanresource.common.exception.ResourceNotFoundException;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.event.SalaryChangedEvent;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository repository;
    private final ApplicationEventPublisher eventPublisher;


    @Override
    public Employee createEmployee(Employee employee) {
        Employee saved = repository.save(employee);
        eventPublisher.publishEvent(new SalaryChangedEvent(saved));
        return saved;
    }

    @Override
    public Employee getByEmployeeNumber(String employeeNumber) {
        return repository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Employee", employeeNumber));
    }
}
