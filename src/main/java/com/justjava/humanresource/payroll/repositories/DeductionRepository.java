package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.entity.Deduction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeductionRepository extends JpaRepository<Deduction, Long> {
    List<Deduction> findByStatus(RecordStatus active);
}