package com.justjava.humanresource.hr.repository;

import com.justjava.humanresource.hr.entity.JobGrade;
import com.justjava.humanresource.hr.entity.JobStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;


public interface JobStepRepository extends JpaRepository<JobStep, Long> {
    void deleteByJobGrade(JobGrade jobGrade);
    List<JobStep> findByJobGrade(JobGrade jobGrade);
    Optional<JobStep> findByGrossSalary(BigDecimal grossSalary);
}

