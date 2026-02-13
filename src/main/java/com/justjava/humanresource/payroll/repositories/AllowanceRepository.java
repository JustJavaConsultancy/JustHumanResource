package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.entity.Allowance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AllowanceRepository extends JpaRepository<Allowance, Long> {
    List<Allowance> findByStatus(RecordStatus active);
}