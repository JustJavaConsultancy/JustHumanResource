package com.justjava.humanresource.hr.repository;

import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.PayGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayGroupRepository extends JpaRepository<PayGroup, Long> {

    Optional<PayGroup> findByCode(String code);
    boolean existsByCode(String code);
    long countByStatus(RecordStatus status);
}
