package com.justjava.humanresource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PositionHistoryCleanupRunner implements ApplicationRunner {

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        System.out.println("===== PositionHistoryCleanupRunner V2 START =====");

        // ----------------------------------------------------------------
        // STEP 1: Delete current=false rows where effective_from is AFTER
        // the employee's current=true record's effective_from.
        // These are junk rows created by recalculation passes that ran on
        // days after the current record was established. They will cause
        // collisions on those future dates when recalculation runs again.
        // ----------------------------------------------------------------
        int step1 = em.createNativeQuery("""
            DELETE FROM employee_position_history
            WHERE current = false
              AND id IN (
                SELECT junk.id
                FROM employee_position_history junk
                JOIN employee_position_history live
                  ON junk.employee_id = live.employee_id
                 AND live.current = true
                WHERE junk.current = false
                  AND junk.effective_from > live.effective_from
              )
        """).executeUpdate();
        System.out.println("Step 1 — deleted future-dated junk current=false rows: " + step1);

        // ----------------------------------------------------------------
        // STEP 2: Delete current=false rows where effective_to < effective_from
        // (broken rows with impossible date ranges)
        // ----------------------------------------------------------------
        int step2 = em.createNativeQuery("""
            DELETE FROM employee_position_history
            WHERE current = false
              AND effective_to IS NOT NULL
              AND effective_to < effective_from
        """).executeUpdate();
        System.out.println("Step 2 — deleted rows where effective_to < effective_from: " + step2);

        // ----------------------------------------------------------------
        // STEP 3: Delete ghost current=false rows on same date as current=true
        // ----------------------------------------------------------------
        int step3 = em.createNativeQuery("""
            DELETE FROM employee_position_history
            WHERE current = false
              AND id IN (
                SELECT ghost.id
                FROM employee_position_history ghost
                JOIN employee_position_history live
                  ON ghost.employee_id    = live.employee_id
                 AND ghost.effective_from = live.effective_from
                 AND live.current = true
                WHERE ghost.current = false
              )
        """).executeUpdate();
        System.out.println("Step 3 — deleted ghost current=false rows on same date as current=true: " + step3);

        // ----------------------------------------------------------------
        // STEP 4: Fix current=true rows that incorrectly have effective_to set
        // ----------------------------------------------------------------
        int step4 = em.createNativeQuery("""
            UPDATE employee_position_history
            SET effective_to = NULL
            WHERE current = true
              AND effective_to IS NOT NULL
        """).executeUpdate();
        System.out.println("Step 4 — fixed current=true rows with non-null effective_to: " + step4);

        // ----------------------------------------------------------------
        // STEP 5: Integrity check
        // ----------------------------------------------------------------
        var dupes = em.createNativeQuery("""
            SELECT employee_id, effective_from, current, COUNT(*)
            FROM employee_position_history
            GROUP BY employee_id, effective_from, current
            HAVING COUNT(*) > 1
        """).getResultList();

        if (dupes.isEmpty()) {
            System.out.println("Step 5 — integrity check PASSED: no duplicates remain.");
        } else {
            System.out.println("Step 5 — integrity check FAILED: remaining duplicates:");
            for (Object row : dupes) {
                Object[] r = (Object[]) row;
                System.out.printf("  emp=%s from=%s current=%s count=%s%n", r[0], r[1], r[2], r[3]);
            }
        }

        // ----------------------------------------------------------------
        // STEP 6: Show final state for employee 130 to confirm
        // ----------------------------------------------------------------
        var rows = em.createNativeQuery("""
            SELECT id, employee_id, effective_from, effective_to, current, status
            FROM employee_position_history
            WHERE employee_id = 130
            ORDER BY effective_from, current
        """).getResultList();

        System.out.println("Step 6 — final state for employee 130:");
        for (Object obj : rows) {
            Object[] r = (Object[]) obj;
            System.out.printf("  id=%-6s from=%-12s to=%-12s current=%-6s status=%s%n",
                    r[0], r[2], r[3], r[4], r[5]);
        }

        System.out.println("===== PositionHistoryCleanupRunner V2 END =====");
    }
}