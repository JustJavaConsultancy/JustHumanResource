package com.justjava.humanresource.hr.service;

import com.justjava.humanresource.hr.entity.Employee;

public interface EmployeeService {

    Employee createEmployee(Employee employee);

    Employee getByEmployeeNumber(String employeeNumber);
}
