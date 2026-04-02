package com.justjava.humanresource.hr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeDocumentDTO {
    private Long id;
    private String documentName;
    private String fileName;
    private String fileType;
    private LocalDateTime uploadedAt;
    private String uploadedBy;
}
