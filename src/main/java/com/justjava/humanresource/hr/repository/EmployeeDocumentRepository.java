package com.justjava.humanresource.hr.repository;

import com.justjava.humanresource.hr.entity.EmployeeDocument;
import com.justjava.humanresource.documents.EmployeeDocumentLibraryRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {


    @Query("SELECT new com.justjava.humanresource.hr.entity.EmployeeDocument(" +
            "d.id, d.documentName, d.fileName, d.fileType, d.uploadedAt, d.uploadedBy) " +
            "FROM EmployeeDocument d WHERE d.employee.id = :employeeId " +
            "ORDER BY d.uploadedAt DESC")
    List<EmployeeDocument> findByEmployeeIdWithoutFileData(@Param("employeeId") Long employeeId);

    // Keep the original for when you actually need the file bytes (like downloading)
    List<EmployeeDocument> findByEmployeeIdOrderByUploadedAtDesc(Long employeeId);

    @Query("""
            SELECT new com.justjava.humanresource.documents.EmployeeDocumentLibraryRow(
                d.id,
                d.documentName,
                d.fileName,
                d.fileType,
                d.uploadedAt,
                d.uploadedBy,
                e.id,
                CONCAT(e.firstName, ' ', e.lastName),
                e.employeeNumber,
                jg.id
            )
            FROM EmployeeDocument d
            JOIN d.employee e
            LEFT JOIN e.jobStep js
            LEFT JOIN js.jobGrade jg
            ORDER BY d.uploadedAt DESC
            """)
    List<EmployeeDocumentLibraryRow> findAllForDocumentLibrary();

    @Query("""
            SELECT new com.justjava.humanresource.documents.EmployeeDocumentLibraryRow(
                d.id,
                d.documentName,
                d.fileName,
                d.fileType,
                d.uploadedAt,
                d.uploadedBy,
                e.id,
                CONCAT(e.firstName, ' ', e.lastName),
                e.employeeNumber,
                jg.id
            )
            FROM EmployeeDocument d
            JOIN d.employee e
            LEFT JOIN e.jobStep js
            LEFT JOIN js.jobGrade jg
            WHERE e.id = :employeeId
            ORDER BY d.uploadedAt DESC
            """)
    List<EmployeeDocumentLibraryRow> findByEmployeeIdForDocumentLibrary(@Param("employeeId") Long employeeId);
}
