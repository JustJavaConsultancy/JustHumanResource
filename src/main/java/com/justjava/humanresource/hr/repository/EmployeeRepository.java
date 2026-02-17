package com.justjava.humanresource.hr.repository;

import com.justjava.humanresource.hr.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee,Long> {

    Optional<Employee> findByEmployeeNumber(String employeeNumber);
    @Query("""
   SELECT COUNT(e)
   FROM Employee e
   WHERE e.employmentStatus = 'ACTIVE'
""")
    long countByEmploymentStatusActive();
}
