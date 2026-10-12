package com.justjava.humanresource.promotion.repository;

import com.justjava.humanresource.promotion.entity.PromotionActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionActivityLogRepository extends JpaRepository<PromotionActivityLog, Long> {

    List<PromotionActivityLog> findByPromotionRequest_IdOrderByCreatedAtDesc(Long requestId);
}
