package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.TaxRelief;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxReliefRepository extends JpaRepository<TaxRelief, Long> {
    List<TaxRelief> findByActiveTrue(Sort sort);
    Optional<TaxRelief> findByCode(String code);
}
