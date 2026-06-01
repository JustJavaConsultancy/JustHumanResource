package com.justjava.humanresource.workflow.delegate.leave;

import com.justjava.humanresource.leave.entity.LeaveApprovalStep;
import com.justjava.humanresource.leave.entity.LeaveRequest;
import com.justjava.humanresource.leave.enums.LeaveApprovalDecision;
import com.justjava.humanresource.leave.repository.LeaveApprovalStepRepository;
import com.justjava.humanresource.leave.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProcessLeaveApprovalDecisionDelegate implements JavaDelegate {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveApprovalStepRepository leaveApprovalStepRepository;

    @Override
    public void execute(DelegateExecution execution) {
        Long leaveRequestId = ((Number) execution.getVariable("leaveRequestId")).longValue();
        Integer currentLevel = ((Number) execution.getVariable("currentLevel")).intValue();
        String decision = String.valueOf(execution.getVariable("approvalDecision"));
        String comment = execution.getVariable("approvalComment") != null
                ? String.valueOf(execution.getVariable("approvalComment"))
                : null;

        List<String> approverIds = (List<String>) execution.getVariable("approverIds");
        int currentSequenceNo = currentLevel + 1;
        Long currentApproverId = Long.parseLong(approverIds.get(currentLevel));

        LeaveApprovalStep step = leaveApprovalStepRepository
                .findByLeaveRequestIdAndSequenceNo(leaveRequestId, currentSequenceNo)
                .orElseThrow(() -> new IllegalStateException("Approval step not found for leave request " + leaveRequestId));

        if (!step.getApproverEmployeeId().equals(currentApproverId)) {
            throw new IllegalStateException("Approval step approver mismatch.");
        }

        if ("REJECT".equalsIgnoreCase(decision)) {
            step.setDecision(LeaveApprovalDecision.REJECTED);
            step.setComments(comment);
            step.setDecisionAt(LocalDateTime.now());
            leaveApprovalStepRepository.save(step);
            execution.setVariable("hasMoreApprovers", false);
            return;
        }

        step.setDecision(LeaveApprovalDecision.APPROVED);
        step.setComments(comment);
        step.setDecisionAt(LocalDateTime.now());
        leaveApprovalStepRepository.save(step);

        int nextLevel = currentLevel + 1;
        boolean hasMore = nextLevel < approverIds.size();
        execution.setVariable("currentLevel", nextLevel);
        execution.setVariable("hasMoreApprovers", hasMore);

        LeaveRequest request = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new IllegalStateException("Leave request not found: " + leaveRequestId));

        if (hasMore) {
            execution.setVariable("currentApproverId", approverIds.get(nextLevel));
            int nextSequenceNo = nextLevel + 1;
            request.setCurrentApprovalLevel(nextSequenceNo);
            leaveRequestRepository.save(request);

            leaveApprovalStepRepository.findByLeaveRequestIdAndSequenceNo(leaveRequestId, nextSequenceNo)
                    .orElseGet(() -> {
                        LeaveApprovalStep nextStep = new LeaveApprovalStep();
                        nextStep.setLeaveRequestId(leaveRequestId);
                        nextStep.setSequenceNo(nextSequenceNo);
                        nextStep.setApproverEmployeeId(Long.parseLong(approverIds.get(nextLevel)));
                        nextStep.setDecision(LeaveApprovalDecision.PENDING);
                        return leaveApprovalStepRepository.save(nextStep);
                    });
        }
    }
}
