package com.justjava.humanresource.hr.service;

import com.justjava.humanresource.hr.dto.EmployeeDocumentDTO;
import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.EmployeeDocument;
import com.justjava.humanresource.hr.repository.EmployeeDocumentRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeDocumentService {

    private final EmployeeDocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public void uploadDocument(Long employeeId, String documentName, MultipartFile file) throws IOException {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeDocument doc = EmployeeDocument.builder()
                .employee(employee)
                .documentName(documentName)
                .fileName(file.getOriginalFilename())
                .fileType(file.getContentType())
                .fileData(file.getBytes())
                .uploadedAt(LocalDateTime.now())
                .uploadedBy("humanResource")
                .build();

        documentRepository.save(doc);
    }


    @Transactional(readOnly = true)
    public List<EmployeeDocumentDTO> getEmployeeDocuments(Long employeeId) {

        return documentRepository.findByEmployeeIdWithoutFileData(employeeId)
                .stream()
                .map(doc -> EmployeeDocumentDTO.builder()
                        .id(doc.getId())
                        .documentName(doc.getDocumentName())
                        .fileName(doc.getFileName())
                        .fileType(doc.getFileType())
                        .uploadedAt(doc.getUploadedAt())
                        .uploadedBy(doc.getUploadedBy())
                        .build())
                .collect(Collectors.toList());
    }

    public EmployeeDocument getDocumentFile(Long docId) {
        return documentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
    }

    @Transactional
    public void deleteDocument(Long docId) {
        documentRepository.deleteById(docId);
    }
}