package com.justjava.humanresource.hr.repository;

import com.justjava.humanresource.hr.entity.EmployeeDocument;
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
}