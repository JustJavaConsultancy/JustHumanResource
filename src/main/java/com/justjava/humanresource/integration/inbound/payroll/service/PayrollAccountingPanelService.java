package com.justjava.humanresource.integration.inbound.payroll.service;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.entity.IntegrationAlert;
import com.justjava.humanresource.integration.inbound.payroll.entity.PayrollJournalSnapshot;
import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertStatus;
import com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus;
import com.justjava.humanresource.integration.inbound.payroll.repository.IntegrationAlertRepository;
import com.justjava.humanresource.integration.inbound.payroll.repository.PayrollJournalSnapshotRepository;
import com.justjava.humanresource.integration.inbound.payroll.service.IntegrationAlertService;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;




@Service
@RequiredArgsConstructor
public class PayrollAccountingPanelService {
    private final IntegrationPayrollProperties properties;
    private final PayrollJournalSnapshotRepository snapshots;
    private final IntegrationAlertRepository alerts;
    private final IntegrationAlertService alertService;

    @Transactional(readOnly = true)
    public PanelData panel() {
        if (!properties.isEnabled() || properties.getCompanyId() == null) {
            return new PanelData(List.of(), List.of());
        }
        List<PayrollJournalSnapshot> recent = snapshots
                .findTop20ByCompanyIdAndStatusNotOrderByPeriodEndDesc(
                        properties.getCompanyId(), SnapshotStatus.VOID);
        List<Long> periodIds = recent.stream().map(PayrollJournalSnapshot::getPayrollPeriodId).toList();
        List<IntegrationAlert> openAlerts = periodIds.isEmpty()
                ? List.of()
                : alerts.findByPayrollPeriodIdInAndStatusOrderByCreatedAtDesc(
                        periodIds, IntegrationAlertStatus.OPEN);
        return new PanelData(
                recent.stream().map(snapshot -> new SnapshotView(
                        snapshot.getJournalReference(),
                        displayStatus(snapshot.getStatus()),
                        snapshot.getPeriodStart(),
                        snapshot.getPeriodEnd(),
                        snapshot.getStatusChangedAt())).toList(),
                openAlerts);
    }

    public void acknowledge(long alertId, String actor) {
        alertService.acknowledge(alertId, actor);
    }

    private static String displayStatus(SnapshotStatus status) {
        return switch (status) {
            case PENDING_PUBLISH -> "Pending";
            case PUBLISHED -> "Sent";
            case POSTED -> "Posted to AMS";
            case REJECTED -> "Rejected";
            case VOID -> "Void";
        };
    }

    public record PanelData(List<SnapshotView> snapshots, List<IntegrationAlert> alerts) {
    }

    public record SnapshotView(
            String journalReference,
            String status,
            java.time.LocalDate periodStart,
            java.time.LocalDate periodEnd,
            LocalDateTime statusChangedAt) {
    }
}
