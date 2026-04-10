package com.justjava.humanresource.hr.repository;

import com.justjava.humanresource.hr.entity.JobGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobGradeRepository extends JpaRepository<JobGrade, Long> {
    Optional<JobGrade> findByName(String name);
}
