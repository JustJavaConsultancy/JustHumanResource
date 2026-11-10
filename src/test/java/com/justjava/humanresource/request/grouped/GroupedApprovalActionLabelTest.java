package com.justjava.humanresource.request.grouped;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupedApprovalActionLabelTest {
    @Mock GroupedApprovalAssignmentRepository assignments;
    @Mock GroupedApprovalLevelRepository levels;
    @InjectMocks GroupedApprovalService service;

    @Test void usesLastLevelInTaskCycleForBothPolicies() {
        var assignment = new GroupedApprovalAssignment();
        assignment.setLevelId(10L);
        var first = new GroupedApprovalLevel();
        first.setCycleId(100L); first.setOrdinal(1);
        var last = new GroupedApprovalLevel();
        last.setCycleId(100L); last.setOrdinal(2);
        when(assignments.findByTaskId("task")).thenReturn(Optional.of(assignment));
        when(levels.findById(10L)).thenReturn(Optional.of(first), Optional.of(last));
        when(levels.findByCycleIdOrderByOrdinal(100L)).thenReturn(List.of(first, last));
        first.setPolicy(ApprovalPolicy.ALL); last.setPolicy(ApprovalPolicy.ANY_ONE);
        assertEquals("Recommend", service.approvalActionLabel("task"));
        assertEquals("Approve", service.approvalActionLabel("task"));
    }
}
