package com.justjava.humanresource.integration.inbound.payroll.controller;

import com.justjava.humanresource.integration.inbound.payroll.dto.IntegrationSnapshotContract;
import com.justjava.humanresource.integration.inbound.payroll.dto.PayrollJournalStatusCallback;
import com.justjava.humanresource.integration.inbound.payroll.dto.PayrollJournalStatusRequest;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollJournalSnapshotService;
import com.justjava.humanresource.integration.inbound.payroll.service.PayrollJournalStatusService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;




@RestController
@RequestMapping("/api/integrations/payroll-journals")
public class PayrollJournalIntegrationController {
    private final PayrollJournalSnapshotService snapshots;
    private final PayrollJournalStatusService statuses;

    public PayrollJournalIntegrationController(
            PayrollJournalSnapshotService snapshots,
            PayrollJournalStatusService statuses) {
        this.snapshots = snapshots;
        this.statuses = statuses;
    }

    @GetMapping("/{journalReference}")
    public IntegrationSnapshotContract get(
            @PathVariable String journalReference) {
        return snapshots.findReadable(journalReference);
    }

    @PostMapping("/{journalReference}/status")
    public ResponseEntity<Void> status(
            @PathVariable String journalReference,
            @Valid @RequestBody PayrollJournalStatusCallback body) {
        statuses.record(journalReference, new PayrollJournalStatusRequest(
                body.status(), body.amsJournalId(), body.reasonCode(), body.message()));
        return ResponseEntity.ok().build();
    }
}
