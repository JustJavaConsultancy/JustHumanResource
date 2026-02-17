package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.PayrollAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollAuditLogRepository
        extends JpaRepository<PayrollAuditLog, Long> {
}
