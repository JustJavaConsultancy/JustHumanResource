package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.payroll.dto.PayrollLockChangeReportDTO;
import com.justjava.humanresource.workflow.dto.FlowableTaskDTO;

import java.util.List;
import java.util.Map;

/**
 * Read-only comparison of "what HR locked" against "what exists now"
 * for a pending Finance lock-approval task.
 */
public interface PayrollLockChangeService {

    PayrollLockChangeReportDTO buildReport(FlowableTaskDTO task);

    /** Key = taskId. */
    Map<String, PayrollLockChangeReportDTO> buildReports(List<FlowableTaskDTO> tasks);
}
