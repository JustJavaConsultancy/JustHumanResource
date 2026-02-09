package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.payroll.entity.PayGroupAllowance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayGroupAllowanceRepository extends JpaRepository<PayGroupAllowance, Long> {

    List<PayGroupAllowance> findByPayGroup(PayGroup payGroup);

}