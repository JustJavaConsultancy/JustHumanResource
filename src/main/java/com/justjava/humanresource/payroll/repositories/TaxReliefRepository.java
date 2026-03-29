package com.justjava.humanresource.payroll.repositories;

import com.justjava.humanresource.payroll.entity.TaxRelief;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaxReliefRepository extends JpaRepository<TaxRelief, Long> {

    List<TaxRelief> findByActiveTrue();
}