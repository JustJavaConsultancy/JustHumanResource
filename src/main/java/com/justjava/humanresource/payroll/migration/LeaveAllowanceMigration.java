package com.justjava.humanresource.payroll.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * ONE-TIME MIGRATION — DELETE THIS CLASS AFTER SUCCESSFUL DEPLOYMENT.
 *
 * Updates the "Leave All (Yearly Value)" allowance:
 *  - Renames it to "Leave All"
 *  - Changes calculation type to PERCENTAGE_OF_GROSS
 *  - Sets percentage rate to 3.829
 *  - Clears the formula expression (no longer needed)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeaveAllowanceMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE allowances
                    SET
                        name               = 'Leave All',
                        calculation_type   = 'PERCENTAGE_OF_GROSS',
                        percentage_rate    = 3.829,
                        formula_expression = NULL
                    WHERE
                        name           = 'Leave All (Annual Value)'
                        AND out_of_payroll = TRUE
                    """
            );

            if (updated == 1) {
                log.info("LeaveAllowanceMigration: allowance updated successfully.");
            } else if (updated == 0) {
                log.warn("LeaveAllowanceMigration: no matching allowance found — check the name or out_of_payroll flag.");
            } else {
                log.warn("LeaveAllowanceMigration: {} rows updated (expected 1) — review your data.", updated);
            }

        } catch (Exception e) {
            log.error("LeaveAllowanceMigration failed: {}", e.getMessage(), e);
        }
    }
}
