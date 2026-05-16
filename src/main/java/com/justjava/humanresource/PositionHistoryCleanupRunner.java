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
        System.out.println("===== PositionHistoryCleanupRunner START =====");

        // ----------------------------------------------------------------
        // STEP 1: Delete ALL current=false rows that share the same
        // (employee_id, effective_from) as a current=true row.
        // These are the rows causing the duplicate key collision —
        // they were created when a previous recalculation closed a record
        // on the same date the current record started.
        // We keep the current=true row (the real one) and delete the ghost.
        // ----------------------------------------------------------------
        int step1 = em.createNativeQuery("""
            DELETE FROM employee_position_history
            WHERE current = false
              AND id IN (
                SELECT ghost.id
                FROM employee_position_history ghost
                JOIN employee_position_history live
                  ON ghost.employee_id   = live.employee_id
                 AND ghost.effective_from = live.effective_from
                 AND live.current = true
                WHERE ghost.current = false
              )
        """).executeUpdate();
        System.out.println("Step 1 — deleted ghost current=false rows on same date as current=true: " + step1);

        // ----------------------------------------------------------------
        // STEP 2: Delete junk current=false rows where effective_to is
        // BEFORE effective_from (completely broken rows like id=1118 above).
        // These were created by botched recalculation passes.
        // ----------------------------------------------------------------
        int step2 = em.createNativeQuery("""
            DELETE FROM employee_position_history
            WHERE current = false
              AND effective_to IS NOT NULL
              AND effective_to < effective_from
        """).executeUpdate();
        System.out.println("Step 2 — deleted rows where effective_to < effective_from: " + step2);

        // ----------------------------------------------------------------
        // STEP 3: Delete duplicate current=false rows on the same date,
        // keeping only the one with the highest id (most recent write).
        // These are left over from multiple recalculation loop iterations
        // hitting the same employee on the same day.
        // ----------------------------------------------------------------
        int step3 = em.createNativeQuery("""
            DELETE FROM employee_position_history
            WHERE current = false
              AND id NOT IN (
                SELECT MAX(id)
                FROM employee_position_history
                WHERE current = false
                GROUP BY employee_id, effective_from
              )
        """).executeUpdate();
        System.out.println("Step 3 — deleted duplicate current=false rows keeping latest: " + step3);

        // ----------------------------------------------------------------
        // STEP 4: For each employee, ensure the current=true row has
        // effective_to = NULL (some may have been incorrectly set).
        // ----------------------------------------------------------------
        int step4 = em.createNativeQuery("""
            UPDATE employee_position_history
            SET effective_to = NULL
            WHERE current = true
              AND effective_to IS NOT NULL
        """).executeUpdate();
        System.out.println("Step 4 — fixed current=true rows that had non-null effective_to: " + step4);

        // ----------------------------------------------------------------
        // STEP 5: Final integrity check — report any remaining anomalies
        // ----------------------------------------------------------------
        var dupes = em.createNativeQuery("""
            SELECT employee_id, effective_from, current, COUNT(*)
            FROM employee_position_history
            GROUP BY employee_id, effective_from, current
            HAVING COUNT(*) > 1
        """).getResultList();

        if (dupes.isEmpty()) {
            System.out.println("Step 5 — integrity check PASSED: no duplicate (employee_id, effective_from, current) combinations remain.");
        } else {
            System.out.println("Step 5 — integrity check FAILED: remaining duplicates:");
            for (Object row : dupes) {
                Object[] r = (Object[]) row;
                System.out.printf("  emp=%s from=%s current=%s count=%s%n", r[0], r[1], r[2], r[3]);
            }
        }

        System.out.println("===== PositionHistoryCleanupRunner END =====");
    }
}
