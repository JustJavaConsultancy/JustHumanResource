package com.justjava.humanresource.orgStructure.repositories;

import com.justjava.humanresource.orgStructure.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    @Query(value = """
        WITH RECURSIVE company_tree AS (
            SELECT id, parent_company_id
            FROM companies
            WHERE id = :parentId

            UNION

            SELECT c.id, c.parent_company_id
            FROM companies c
            INNER JOIN company_tree ct
                ON c.parent_company_id = ct.id
        )
        SELECT COUNT(*) > 0
        FROM company_tree
        WHERE id = :childId
    """, nativeQuery = true)
    boolean wouldCreateCycle(
            @Param("parentId") Long parentId,
            @Param("childId") Long childId
    );
}
