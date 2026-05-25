package com.justjava.humanresource.leave.repository;

import com.justjava.humanresource.leave.entity.LeaveApprovalStep;
import com.justjava.humanresource.leave.enums.LeaveApprovalDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveApprovalStepRepository extends JpaRepository<LeaveApprovalStep, Long> {
    List<LeaveApprovalStep> findByLeaveRequestIdOrderBySequenceNoAsc(Long leaveRequestId);
    Optional<LeaveApprovalStep> findByLeaveRequestIdAndSequenceNo(Long leaveRequestId, Integer sequenceNo);
    Optional<LeaveApprovalStep> findByLeaveRequestIdAndApproverEmployeeIdAndDecision(
            Long leaveRequestId,
            Long approverEmployeeId,
            LeaveApprovalDecision decision
    );
}
