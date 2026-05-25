package com.justjava.humanresource.hr.service;

import org.springframework.web.multipart.MultipartFile;

public interface EmployeePayItemUploadService {
    UploadSummary uploadPayItems(MultipartFile file);

    record UploadSummary(int totalRows, int successRows) {}
}
