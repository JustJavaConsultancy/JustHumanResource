package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.PayrollAccountingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollAccountingSettingsRepository
        extends JpaRepository<PayrollAccountingSettings, Long> {

    Optional<PayrollAccountingSettings> findByCompanyId(Long companyId);
}
