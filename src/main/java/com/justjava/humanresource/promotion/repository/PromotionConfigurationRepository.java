package com.justjava.humanresource.promotion.repository;

import com.justjava.humanresource.promotion.entity.PromotionConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromotionConfigurationRepository extends JpaRepository<PromotionConfiguration, Long> {

    Optional<PromotionConfiguration> findFirstByActiveTrueOrderByUpdatedAtDescIdDesc();

    List<PromotionConfiguration> findAllByOrderByUpdatedAtDescIdDesc();
}
