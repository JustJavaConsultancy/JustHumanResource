package com.justjava.humanresource.recruitment.repository;

import com.justjava.humanresource.recruitment.entity.AiShortlistingRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiShortlistingRunRepository extends JpaRepository<AiShortlistingRun, Long> {
    List<AiShortlistingRun> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
    Optional<AiShortlistingRun> findFirstByApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
