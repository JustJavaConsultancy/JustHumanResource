package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.payroll.entity.Deduction;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeductionRepository extends JpaRepository<Deduction, Long> {
    List<Deduction> findByStatus(RecordStatus status, Sort sort);
    Optional<Deduction> findByCode(String code);
}
