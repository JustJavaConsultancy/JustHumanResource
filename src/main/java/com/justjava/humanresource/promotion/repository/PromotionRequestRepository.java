package com.justjava.humanresource.promotion.repository;

import com.justjava.humanresource.promotion.entity.PromotionRequest;
import com.justjava.humanresource.promotion.entity.PromotionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PromotionRequestRepository extends JpaRepository<PromotionRequest, Long>, JpaSpecificationExecutor<PromotionRequest> {

    Optional<PromotionRequest> findByRequestNumber(String requestNumber);

    List<PromotionRequest> findByStatus(PromotionStatus status);

    List<PromotionRequest> findByStatusIn(Collection<PromotionStatus> statuses);

    List<PromotionRequest> findByEmployee_IdOrderByRequestedAtDesc(Long employeeId);

    List<PromotionRequest> findByEmployee_IdOrRequestedBy_Id(Long employeeId, Long requestedById);

    boolean existsByEmployee_IdAndStatusIn(Long employeeId, Collection<PromotionStatus> statuses);

    long countByStatus(PromotionStatus status);

    long countByStatusIn(Collection<PromotionStatus> statuses);

    long countByStatusAndApprovedAtBetween(PromotionStatus status, java.time.LocalDateTime start, java.time.LocalDateTime end);

    List<PromotionRequest> findByEffectiveDateBetween(LocalDate start, LocalDate end);
}
