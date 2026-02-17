package com.justjava.humanresource.payroll.repositories;


import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.entity.PayrollPeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {
    Optional<PayrollPeriod> findByYearAndMonth(int year, int month);
    Optional<PayrollPeriod> findByStatus(PayrollPeriodStatus status);
}
