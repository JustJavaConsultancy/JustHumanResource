package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import com.justjava.humanresource.kpi.entity.KpiAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeAppraisalRepository extends JpaRepository<EmployeeAppraisal, Long> {
}
