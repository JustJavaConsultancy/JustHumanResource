package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.payroll.entity.EmployeeAllowance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeAllowanceRepository extends JpaRepository<EmployeeAllowance, Long> {

    List<EmployeeAllowance> findByEmployee(Employee employee);
}