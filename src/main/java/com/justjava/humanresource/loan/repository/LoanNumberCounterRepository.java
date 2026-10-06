package com.justjava.humanresource.loan.repository;

import com.justjava.humanresource.loan.entity.LoanNumberCounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoanNumberCounterRepository extends JpaRepository<LoanNumberCounter, Long> {

    /** Pessimistic lock so concurrent submissions never receive the same number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LoanNumberCounter c where c.prefix = :prefix and c.year = :year")
    Optional<LoanNumberCounter> findForUpdate(@Param("prefix") String prefix, @Param("year") Integer year);
}
