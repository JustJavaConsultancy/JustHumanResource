package com.justjava.humanresource.workflow.delegate.onboarding;


import com.justjava.humanresource.aau.keycloak.KeycloakAdminService;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;

@Slf4j
@Component("keycloakUserCreationDelegate")
@RequiredArgsConstructor
public class KeycloakUserCreationDelegate implements JavaDelegate {

    private final KeycloakAdminService keycloakAdminService;
    private final EmployeeService employeeService;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) {

        String processInstanceId = execution.getProcessInstanceId();
        Long employeeId = (Long) execution.getVariable("employeeId");

        Assert.notNull(employeeId, "employeeId process variable is required");

        log.info("Starting Keycloak provisioning for employeeId={}, processInstanceId={}",
                employeeId, processInstanceId);

        // =========================================================
        // 1️⃣ Load Employee from Database (Source of Truth)
        // =========================================================

        Employee employee = employeeService.getById(employeeId);

        // =========================================================
        // 2️⃣ Idempotency Check (CRITICAL)
        // =========================================================

        if (employee.getKeycloakUserId() != null) {

            log.info("Employee {} already provisioned in Keycloak. keycloakUserId={}",
                    employeeId, employee.getKeycloakUserId());

            execution.setVariable("keycloakUserId", employee.getKeycloakUserId());
            execution.setVariable("keycloakUserCreated", true);

            return;
        }

        // =========================================================
        // 3️⃣ Build Attributes From Domain Model
        // =========================================================

        Map<String, List<String>> attributes = Map.of(
                "employeeId", List.of(String.valueOf(employee.getId())),
                "staffNumber", List.of(employee.getEmployeeNumber()),
                "companyId", List.of(String.valueOf(employee.getDepartment().getCompany().getId())),
                "sourceSystem", List.of("HRMS")
        );



        System.out.println(" THe Attributes Here=============="+attributes);
        // =========================================================
        // 4️⃣ Determine Username & Password Strategy
        // =========================================================

        String username = employee.getEmail(); // using email as username
        String email = employee.getEmail();

        String temporaryPassword = employeeService.generateInitialPassword(employee);

        // =========================================================
        // 5️⃣ Create User in Keycloak
        // =========================================================

        try {

            String keycloakUserId = keycloakAdminService.createUser(
                    username,
                    email,
                    temporaryPassword,
                    employee.getFirstName(),
                    employee.getLastName(),
                    attributes
            );
            keycloakAdminService.sendPasswordResetEmail(keycloakUserId);
            System.out.println(" The Created User keycloakUserId===="+keycloakUserId);
            // =====================================================
            // 6️⃣ Persist Keycloak ID Back To Employee
            // =====================================================

            employee.setKeycloakUserId(keycloakUserId);
            employeeService.save(employee);

            // =====================================================
            // 7️⃣ Update Process Variables
            // =====================================================

            execution.setVariable("keycloakUserId", keycloakUserId);
            execution.setVariable("keycloakUserCreated", true);

            log.info("Successfully provisioned Keycloak user for employeeId={}, keycloakUserId={}",
                    employeeId, keycloakUserId);

        } catch (Exception ex) {
            log.error("Keycloak provisioning failed for employeeId={}", employeeId, ex);

            // Let Flowable retry according to retry policy
            throw ex;
        }
    }
}