package com.justjava.humanresource.recruitment.repository;

import com.justjava.humanresource.recruitment.entity.ApplicationStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApplicationStageHistoryRepository extends JpaRepository<ApplicationStageHistory, Long> {
    List<ApplicationStageHistory> findByApplicationIdOrderByCreatedAt(Long applicationId);
}
