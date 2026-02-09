package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.payroll.entity.PayGroupDeduction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayGroupDeductionRepository extends JpaRepository<PayGroupDeduction, Long> {

    List<PayGroupDeduction> findByPayGroup(PayGroup payGroup);
}