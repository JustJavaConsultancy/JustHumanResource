package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.Allowance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllowanceRepository extends JpaRepository<Allowance, Long> {
}