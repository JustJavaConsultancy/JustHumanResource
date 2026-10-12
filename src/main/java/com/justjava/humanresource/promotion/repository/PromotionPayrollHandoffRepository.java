package com.justjava.humanresource.promotion.repository;

import com.justjava.humanresource.promotion.entity.PromotionPayrollHandoff;
import com.justjava.humanresource.promotion.enums.PromotionPayrollHandoffStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionPayrollHandoffRepository extends JpaRepository<PromotionPayrollHandoff, Long> {

    Optional<PromotionPayrollHandoff> findByPromotionRequest_Id(Long requestId);

    boolean existsByPromotionRequest_Id(Long requestId);

    List<PromotionPayrollHandoff> findByStatusOrderByEffectiveDateAscIdAsc(PromotionPayrollHandoffStatus status);

    List<PromotionPayrollHandoff> findAllByOrderByEffectiveDateAscIdAsc();
}
