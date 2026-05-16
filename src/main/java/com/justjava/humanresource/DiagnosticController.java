package com.justjava.humanresource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

        import java.util.*;

@RestController
@RequestMapping("/internal/diagnostic")
@RequiredArgsConstructor
public class DiagnosticController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/position-history/{employeeId}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getPositionHistory(
            @PathVariable Long employeeId) {

        List<?> rows = em.createNativeQuery("""
            SELECT id, employee_id, job_step_id, department_id, pay_group_id,
                   effective_from, effective_to, current, status
            FROM employee_position_history
            WHERE employee_id = :empId
            ORDER BY effective_from, current
        """).setParameter("empId", employeeId).getResultList();

        List<Map<String, Object>> records = new ArrayList<>();
        for (Object obj : rows) {
            Object[] r = (Object[]) obj;
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id",            r[0]);
            record.put("employee_id",   r[1]);
            record.put("job_step_id",   r[2]);
            record.put("department_id", r[3]);
            record.put("pay_group_id",  r[4]);
            record.put("effective_from",r[5]);
            record.put("effective_to",  r[6]);
            record.put("current",       r[7]);
            record.put("status",        r[8]);
            records.add(record);
        }

        List<?> dupes = em.createNativeQuery("""
            SELECT employee_id, effective_from, current, COUNT(*) as cnt
            FROM employee_position_history
            GROUP BY employee_id, effective_from, current
            HAVING COUNT(*) > 1
        """).getResultList();

        List<Map<String, Object>> dupeList = new ArrayList<>();
        for (Object obj : dupes) {
            Object[] r = (Object[]) obj;
            Map<String, Object> dupe = new LinkedHashMap<>();
            dupe.put("employee_id",   r[0]);
            dupe.put("effective_from",r[1]);
            dupe.put("current",       r[2]);
            dupe.put("count",         r[3]);
            dupeList.add(dupe);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("employee_id", employeeId);
        response.put("position_history", records);
        response.put("all_duplicates_in_table", dupeList);

        return ResponseEntity.ok(response);
    }
}
