package com.justjava.humanresource.payroll.statutory.service;

import org.springframework.web.multipart.MultipartFile;

public interface TaxBandUploadService {
    UploadSummary uploadTaxBands(MultipartFile file);

    record UploadSummary(int totalRows, int successRows, String regimeCode) {}
}