package com.justjava.humanresource.integration.inbound.payroll.service;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertType;
import com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus;
import com.justjava.humanresource.integration.inbound.payroll.repository.PayrollJournalSnapshotRepository;
import com.justjava.humanresource.integration.inbound.payroll.service.IntegrationAlertService;

import com.justjava.humanresource.payroll.entity.PayrollPeriod;
import com.justjava.humanresource.payroll.enums.PayrollPeriodStatus;
import com.justjava.humanresource.payroll.repositories.PayrollPeriodRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;




@Service
@RequiredArgsConstructor
public class PayrollAccountingChangeDetector {
    private final IntegrationPayrollProperties properties;
    private final PayrollPeriodRepository periods;
    private final PayrollJournalSnapshotRepository snapshots;
    private final IntegrationAlertService alerts;

    @Transactional
    public void detect(Long payrollRunId, Long companyId, LocalDate periodStart, LocalDate periodEnd) {
        if (!properties.isEnabled() || properties.getCompanyId() == null
                || !properties.getCompanyId().equals(companyId)
                || payrollRunId == null || periodStart == null || periodEnd == null) {
            return;
        }
        PayrollPeriod matched = periods
                .findByCompanyIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                        companyId, periodStart, periodEnd)
                .orElse(null);
        if (matched == null) {
            return;
        }
        PayrollPeriod period = periods.findByIdForUpdate(matched.getId()).orElseThrow();
        if (period.getStatus() != PayrollPeriodStatus.CLOSED
                || snapshots.findByPayrollPeriodIdAndStatusNot(period.getId(), SnapshotStatus.VOID).isEmpty()) {
            return;
        }
        alerts.createIfAbsent(
                IntegrationAlertType.PAYROLL_CHANGED_AFTER_SEND,
                "CHANGED:" + payrollRunId,
                null,
                period.getId(),
                "Payroll for " + periodStart + " to " + periodEnd
                        + " changed after the journal was sent to accounting. Adjust AMS manually.");
    }
}
