package com.justjava.humanresource.loan.repository;

import com.justjava.humanresource.loan.entity.EmployeeLoanActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeLoanActivityRepository extends JpaRepository<EmployeeLoanActivity, Long> {

    List<EmployeeLoanActivity> findByLoanApplicationIdOrderByCreatedAtAscIdAsc(Long loanApplicationId);
}
