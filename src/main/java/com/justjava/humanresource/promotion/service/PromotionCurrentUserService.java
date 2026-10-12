package com.justjava.humanresource.promotion.service;

import com.justjava.humanresource.core.config.AuthenticationManager;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PromotionCurrentUserService {

    private final AuthenticationManager authenticationManager;
    private final EmployeeRepository employeeRepository;

    public Optional<Employee> findCurrentEmployee() {
        String email = authenticationManager.getCurrentUserEmail();
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return employeeRepository.findByEmail(email);
    }

    public Employee getCurrentEmployeeOrSystem() {
        return findCurrentEmployee().orElse(null);
    }

    public String currentUserDisplayName() {
        String name = authenticationManager.getCurrentUserName();
        return name == null || name.isBlank() ? "System" : name;
    }
}
