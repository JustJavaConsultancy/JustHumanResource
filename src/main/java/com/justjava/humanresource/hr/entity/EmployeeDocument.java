package com.justjava.humanresource.hr.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "employee_documents")
public class EmployeeDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    private String documentName;
    private String fileName;
    private String fileType;

    @Lob
    @Column(name = "file_data", columnDefinition = "oid")
    private byte[] fileData;

    private LocalDateTime uploadedAt;
    private String uploadedBy;


    // This allows us to fetch metadata without loading the heavy 'fileData' byte array
    public EmployeeDocument(Long id, String documentName, String fileName, String fileType, LocalDateTime uploadedAt, String uploadedBy) {
        this.setId(id); // Sets ID in BaseEntity
        this.documentName = documentName;
        this.fileName = fileName;
        this.fileType = fileType;
        this.uploadedAt = uploadedAt;
        this.uploadedBy = uploadedBy;
    }
}