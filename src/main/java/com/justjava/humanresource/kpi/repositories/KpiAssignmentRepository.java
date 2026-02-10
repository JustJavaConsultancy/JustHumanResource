package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KpiAssignmentRepository
        extends JpaRepository<KpiAssignment, Long> {

    List<KpiAssignment> findByEmployee_Id(Long employeeId);
    List<KpiAssignment> findByJobStep_Id(Long roleId);
}

