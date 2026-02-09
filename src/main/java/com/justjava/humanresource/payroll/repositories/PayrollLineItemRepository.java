package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.PayrollLineItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollLineItemRepository extends JpaRepository<PayrollLineItem, Long> {
}