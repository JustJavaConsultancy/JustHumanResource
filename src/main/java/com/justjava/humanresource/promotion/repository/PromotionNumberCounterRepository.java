package com.justjava.humanresource.promotion.repository;

import com.justjava.humanresource.promotion.entity.PromotionNumberCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromotionNumberCounterRepository extends JpaRepository<PromotionNumberCounter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PromotionNumberCounter c where c.prefix = :prefix and c.year = :year")
    Optional<PromotionNumberCounter> findForUpdate(@Param("prefix") String prefix, @Param("year") int year);
}
