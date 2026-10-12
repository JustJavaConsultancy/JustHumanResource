package com.justjava.humanresource.promotion.repository;

import com.justjava.humanresource.promotion.entity.PromotionEligibilityCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionEligibilityCheckRepository extends JpaRepository<PromotionEligibilityCheck, Long> {

    List<PromotionEligibilityCheck> findByPromotionRequest_IdOrderByIdAsc(Long requestId);

    void deleteByPromotionRequest_Id(Long requestId);
}
