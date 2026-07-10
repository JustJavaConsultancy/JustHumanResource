package com.justjava.humanresource.approval.repository;

import com.justjava.humanresource.approval.entity.CustomApprovalPath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomApprovalPathRepository extends JpaRepository<CustomApprovalPath, Long> {
    Optional<CustomApprovalPath> findByNameIgnoreCase(String name);
    List<CustomApprovalPath> findAllByOrderByNameAsc();
    List<CustomApprovalPath> findByEnabledTrueOrderByNameAsc();
}
