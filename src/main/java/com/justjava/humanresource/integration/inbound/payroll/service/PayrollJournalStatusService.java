package com.justjava.humanresource.integration.inbound.payroll.service;

import com.justjava.humanresource.integration.inbound.payroll.config.IntegrationPayrollProperties;
import com.justjava.humanresource.integration.inbound.payroll.dto.PayrollJournalStatusRequest;
import com.justjava.humanresource.integration.inbound.payroll.entity.PayrollJournalSnapshot;
import com.justjava.humanresource.integration.inbound.payroll.enums.IntegrationAlertType;
import com.justjava.humanresource.integration.inbound.payroll.enums.SnapshotStatus;
import com.justjava.humanresource.integration.inbound.payroll.exception.IntegrationResourceNotFoundException;
import com.justjava.humanresource.integration.inbound.payroll.repository.PayrollJournalSnapshotRepository;
import com.justjava.humanresource.integration.inbound.payroll.service.IntegrationAlertService;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;




@Service
@RequiredArgsConstructor
public class PayrollJournalStatusService {
    private final PayrollJournalSnapshotRepository snapshots;
    private final IntegrationAlertService alerts;
    private final IntegrationPayrollProperties properties;

    @Transactional
    public void record(String reference, PayrollJournalStatusRequest request) {
        if (!properties.isEnabled()) {
            throw new IntegrationResourceNotFoundException();
        }
        if (request == null || !("POSTED".equals(request.status()) || "REJECTED".equals(request.status()))) {
            throw new IllegalArgumentException("Only POSTED and REJECTED statuses are accepted.");
        }
        PayrollJournalSnapshot snapshot = snapshots.findByJournalReferenceForUpdate(reference)
                .filter(row -> row.getStatus() != SnapshotStatus.VOID)
                .orElseThrow(IntegrationResourceNotFoundException::new);
        SnapshotStatus requested = SnapshotStatus.valueOf(request.status());
        if (snapshot.getStatus() == requested) {
            return;
        }
        if (snapshot.getStatus() == SnapshotStatus.POSTED
                || !(snapshot.getStatus() == SnapshotStatus.PUBLISHED
                || (snapshot.getStatus() == SnapshotStatus.REJECTED && requested == SnapshotStatus.POSTED))) {
            throw new IllegalStateException("The accounting status transition is not allowed.");
        }

        snapshot.setStatus(requested);
        snapshot.setStatusChangedAt(LocalDateTime.now());
        snapshot.setAmsJournalId(request.amsJournalId());
        snapshot.setLastReasonCode(sanitize(request.reasonCode(), 100));
        snapshot.setLastReasonMessage(sanitize(request.message(), 500));
        snapshots.save(snapshot);

        if (requested == SnapshotStatus.REJECTED) {
            alerts.createIfAbsent(
                    IntegrationAlertType.AMS_REJECTED,
                    "REJECTED:" + reference,
                    reference,
                    snapshot.getPayrollPeriodId(),
                    "Accounting rejected journal " + reference + ".");
        } else if (requested == SnapshotStatus.POSTED) {
            alerts.resolveRejected(reference);
        }
    }

    private static String sanitize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }
}
