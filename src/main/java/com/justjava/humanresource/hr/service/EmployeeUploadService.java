package com.justjava.humanresource.hr.service;

import org.springframework.web.multipart.MultipartFile;

public interface EmployeeUploadService {
    void uploadEmployees(MultipartFile file);
}