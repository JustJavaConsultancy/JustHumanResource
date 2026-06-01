package com.justjava.humanresource.workflow.delegate.leave;

import com.justjava.humanresource.leave.entity.LeaveRequest;
import com.justjava.humanresource.leave.enums.LeaveRequestStatus;
import com.justjava.humanresource.leave.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FinalizeLeaveRejectionDelegate implements JavaDelegate {

    private final LeaveRequestRepository leaveRequestRepository;

    @Override
    public void execute(DelegateExecution execution) {
        Long leaveRequestId = ((Number) execution.getVariable("leaveRequestId")).longValue();
        LeaveRequest request = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new IllegalStateException("Leave request not found: " + leaveRequestId));
        request.setStatus(LeaveRequestStatus.REJECTED);
        leaveRequestRepository.save(request);
    }
}
