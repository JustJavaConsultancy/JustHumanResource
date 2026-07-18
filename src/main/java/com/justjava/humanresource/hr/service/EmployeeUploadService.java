package com.justjava.humanresource.hr.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface EmployeeUploadService {
    void uploadEmployees(MultipartFile file, List<String> groups);
}