package com.justjava.humanresource.loan.service;

import com.justjava.humanresource.loan.dto.LoanApprovalTaskResponse;

import java.util.List;

/**
 * Task queues and decisions for the loan approval process.
 *
 * Access: HR tasks need HR/admin, Finance tasks need Finance Officer/admin, and in both cases the
 * task must carry the matching candidate group. Custom tasks must be assigned to the current employee
 * (no HR/Finance role needed). Nobody may decide their own loan application. Reject and return need a
 * comment. Approvers never change loan terms.
 */
public interface LoanApprovalService {

    List<LoanApprovalTaskResponse> listHrTasks();

    List<LoanApprovalTaskResponse> listFinanceTasks();

    /** Custom approval tasks assigned to the logged-in employee. */
    List<LoanApprovalTaskResponse> listMyCustomTasks();

    void approveHr(String taskId, String comment);

    void rejectHr(String taskId, String comment);

    void returnHr(String taskId, String comment);

    void approveFinance(String taskId, String comment);

    void rejectFinance(String taskId, String comment);

    void returnFinance(String taskId, String comment);

    void approveCustom(String taskId, String comment);

    void rejectCustom(String taskId, String comment);

    void returnCustom(String taskId, String comment);
}
