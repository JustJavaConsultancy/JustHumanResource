package com.justjava.humanresource.loan.service.impl;

import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.loan.dto.LoanActivityResponse;
import com.justjava.humanresource.loan.entity.EmployeeLoanActivity;
import com.justjava.humanresource.loan.enums.LoanActivityType;
import com.justjava.humanresource.loan.repository.EmployeeLoanActivityRepository;
import com.justjava.humanresource.loan.service.LoanActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanActivityServiceImpl implements LoanActivityService {

    private final EmployeeLoanActivityRepository activityRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional
    public EmployeeLoanActivity record(Long loanApplicationId, LoanActivityType type,
                                       String description, Long actorEmployeeId) {
        return recordForAccount(loanApplicationId, null, type, description, actorEmployeeId);
    }

    @Override
    @Transactional
    public EmployeeLoanActivity recordForAccount(Long loanApplicationId, Long loanAccountId,
                                                 LoanActivityType type, String description, Long actorEmployeeId) {
        EmployeeLoanActivity activity = new EmployeeLoanActivity();
        activity.setLoanApplicationId(loanApplicationId);
        activity.setLoanAccountId(loanAccountId);
        activity.setActivityType(type);
        activity.setActorEmployeeId(actorEmployeeId);
        activity.setDescription(description != null && description.length() > 2000
                ? description.substring(0, 2000) : description);
        return activityRepository.save(activity);
    }

    @Override
    public List<LoanActivityResponse> listForApplication(Long loanApplicationId) {
        List<EmployeeLoanActivity> rows =
                activityRepository.findByLoanApplicationIdOrderByCreatedAtAscIdAsc(loanApplicationId);
        Set<Long> actorIds = rows.stream().map(EmployeeLoanActivity::getActorEmployeeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = actorIds.isEmpty() ? Map.of()
                : employeeRepository.findAllById(actorIds).stream()
                        .collect(Collectors.toMap(Employee::getId, Employee::getFullName));

        return rows.stream().map(a -> LoanActivityResponse.builder()
                .id(a.getId())
                .loanApplicationId(a.getLoanApplicationId())
                .loanAccountId(a.getLoanAccountId())
                .activityType(a.getActivityType())
                .description(a.getDescription())
                .actorEmployeeId(a.getActorEmployeeId())
                .actorName(a.getActorEmployeeId() == null ? "System"
                        : names.getOrDefault(a.getActorEmployeeId(), "Unknown"))
                .createdAt(a.getCreatedAt())
                .build()).toList();
    }

    @Override
    @Transactional
    public void purge(Long loanApplicationId) {
        activityRepository.deleteAll(
                activityRepository.findByLoanApplicationIdOrderByCreatedAtAscIdAsc(loanApplicationId));
    }
}
