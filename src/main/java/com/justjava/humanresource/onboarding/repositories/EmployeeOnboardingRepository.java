package com.justjava.humanresource.onboarding.repositories;

import com.justjava.humanresource.onboarding.entity.EmployeeOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface EmployeeOnboardingRepository
        extends JpaRepository<EmployeeOnboarding, Long> {

    Optional<EmployeeOnboarding> findByProcessInstanceId(String processInstanceId);

    Optional<EmployeeOnboarding> findByEmployee_Id(Long id);

}