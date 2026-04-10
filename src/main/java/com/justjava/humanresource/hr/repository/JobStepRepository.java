package com.justjava.humanresource.hr.repository;

import com.justjava.humanresource.hr.entity.JobGrade;
import com.justjava.humanresource.hr.entity.JobStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface JobStepRepository extends JpaRepository<JobStep, Long> {
    void deleteByJobGrade(JobGrade jobGrade);
    List<JobStep> findByJobGrade(JobGrade jobGrade);
}

