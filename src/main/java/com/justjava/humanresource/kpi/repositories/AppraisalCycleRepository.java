package com.justjava.humanresource.kpi.repositories;

import com.justjava.humanresource.kpi.entity.AppraisalCycle;
import com.justjava.humanresource.kpi.entity.EmployeeAppraisal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.Optional;

public interface AppraisalCycleRepository extends JpaRepository<AppraisalCycle, Long> {
    Optional<AppraisalCycle> findByPeriod(YearMonth current);
}
