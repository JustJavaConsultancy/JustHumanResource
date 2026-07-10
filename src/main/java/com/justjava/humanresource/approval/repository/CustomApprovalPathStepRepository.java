package com.justjava.humanresource.approval.repository;

import com.justjava.humanresource.approval.entity.CustomApprovalPathStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomApprovalPathStepRepository extends JpaRepository<CustomApprovalPathStep, Long> {
    List<CustomApprovalPathStep> findByCustomApprovalPathIdOrderBySequenceNo(Long customApprovalPathId);
    void deleteByCustomApprovalPathId(Long customApprovalPathId);
}
