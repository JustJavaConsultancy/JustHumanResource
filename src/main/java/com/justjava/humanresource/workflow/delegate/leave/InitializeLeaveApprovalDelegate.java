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

import java.util.List;

@Component
@RequiredArgsConstructor
public class InitializeLeaveApprovalDelegate implements JavaDelegate {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveApprovalStepRepository leaveApprovalStepRepository;

    @Override
    public void execute(DelegateExecution execution) {
        Long leaveRequestId = ((Number) execution.getVariable("leaveRequestId")).longValue();
        List<String> approverIds = (List<String>) execution.getVariable("approverIds");

        if (approverIds == null || approverIds.isEmpty()) {
            throw new IllegalStateException("Approver chain is empty for leave request " + leaveRequestId);
        }

        execution.setVariable("currentLevel", 0);
        execution.setVariable("currentApproverId", approverIds.get(0));
        execution.setVariable("hasMoreApprovers", approverIds.size() > 1);

        LeaveRequest request = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new IllegalStateException("Leave request not found: " + leaveRequestId));
        request.setCurrentApprovalLevel(1);
        request.setTotalApprovalLevels(approverIds.size());
        leaveRequestRepository.save(request);

        LeaveApprovalStep step = new LeaveApprovalStep();
        step.setLeaveRequestId(leaveRequestId);
        step.setSequenceNo(1);
        step.setApproverEmployeeId(Long.parseLong(approverIds.get(0)));
        step.setDecision(LeaveApprovalDecision.PENDING);
        leaveApprovalStepRepository.save(step);
    }
}
