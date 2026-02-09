package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.PaySlip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaySlipRepository extends JpaRepository<PaySlip, Long> {
}
