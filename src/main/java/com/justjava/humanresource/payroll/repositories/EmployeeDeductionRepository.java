package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.payroll.entity.EmployeeDeduction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeDeductionRepository extends JpaRepository<EmployeeDeduction, Long> {

    List<EmployeeDeduction> findByEmployee(Employee employee);
}