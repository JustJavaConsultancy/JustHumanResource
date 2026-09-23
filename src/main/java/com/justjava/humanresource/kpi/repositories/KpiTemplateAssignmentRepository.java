package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.KpiTemplateAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KpiTemplateAssignmentRepository extends JpaRepository<KpiTemplateAssignment, Long> {
    List<KpiTemplateAssignment> findByTemplate_IdAndActiveTrue(Long templateId);
    List<KpiTemplateAssignment> findByEmployee_IdAndActiveTrue(Long employeeId);
    List<KpiTemplateAssignment> findByJobStep_IdAndActiveTrue(Long jobStepId);
    List<KpiTemplateAssignment> findByDepartment_IdAndActiveTrue(Long departmentId);
    List<KpiTemplateAssignment> findTop50ByOrderByCreatedAtDesc();
}
