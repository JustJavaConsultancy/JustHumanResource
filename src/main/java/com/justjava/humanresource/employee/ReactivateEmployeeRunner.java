package com.justjava.humanresource.employee;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ONE-TIME UTILITY — DELETE AFTER USE
 *
 * Reactivates employee ID 5:
 * - Sets status = 'ACTIVE'
 * - Clears suspension_from = NULL
 * - Clears suspension_to = NULL
 *
 * Runs automatically on app startup.
 * Delete this class once confirmed successful.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactivateEmployeeRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    // ✅ Change this ID if you need to reactivate a different employee
    private static final long EMPLOYEE_ID = 132;

    @Override
    @Transactional
    public void run(String... args) {
        log.warn("==================================================");
        log.warn("  REACTIVATE EMPLOYEE RUNNER STARTING...");
        log.warn("  Targeting employee ID: {}", EMPLOYEE_ID);
        log.warn("==================================================");

        try {
            int rows = jdbcTemplate.update("""
                    UPDATE employees
                    SET status          = 'ACTIVE',
                        suspension_from = NULL,
                        suspension_to   = NULL
                    WHERE id = ?
                    """, EMPLOYEE_ID);

            if (rows == 0) {
                log.warn("⚠️  No employee found with ID {}. Nothing was updated.", EMPLOYEE_ID);
            } else {
                log.warn("==================================================");
                log.warn("  ✅ Employee ID {} successfully reactivated!", EMPLOYEE_ID);
                log.warn("     status = ACTIVE");
                log.warn("     suspension_from = NULL");
                log.warn("     suspension_to   = NULL");
                log.warn("  ⚠️  Please delete ReactivateEmployeeRunner.java now!");
                log.warn("==================================================");
            }

        } catch (Exception e) {
            log.error("❌ REACTIVATION FAILED — transaction will be rolled back", e);
            throw e;
        }
    }
}