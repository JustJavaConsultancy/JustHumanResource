package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.Deduction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeductionRepository extends JpaRepository<Deduction, Long> {
}