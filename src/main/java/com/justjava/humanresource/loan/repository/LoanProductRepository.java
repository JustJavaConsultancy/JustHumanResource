package com.justjava.humanresource.loan.repository;

import com.justjava.humanresource.loan.entity.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

    List<LoanProduct> findByActiveTrueOrderByNameAsc();

    List<LoanProduct> findAllByOrderByNameAsc();

    Optional<LoanProduct> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
}
