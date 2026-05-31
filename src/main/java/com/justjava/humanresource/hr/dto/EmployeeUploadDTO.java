package com.justjava.humanresource.hr.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Data
public class EmployeeUploadDTO {

    // ── Core / required for creation ──────────────────────────────────────────
    private String firstName;
    private String secondName;
    private String email;
    private String grade;
    private BigDecimal gross;

    // ── Bank details (optional) ───────────────────────────────────────────────
    private String accountName;
    private String bankName;
    private String accountNumber;

    // ── Identification numbers (optional) ────────────────────────────────────
    private String tinNumber;
    private String rsaPin;
    private String pfa;
    private String ninNumber;
    private String bvnNumber;

    // ── Contact & employment (optional) ──────────────────────────────────────
    private String phoneNumber;
    private String dateOfHire;       // yyyy-MM-dd string; parsed to LocalDate in service

    // ── Next of kin (optional) ───────────────────────────────────────────────
    private String nextOfKinName;
    private String nextOfKinPhoneNumber;
    private String nextOfKinEmail;
    private String nextOfKinAddress;

    // ── Guarantor (optional) ─────────────────────────────────────────────────
    private String guarantorName;
    private String guarantorPhoneNumber;
    private String guarantorEmail;
    private String guarantorAddress;
    private String guarantorNinNumber;

    // ── Personal information (optional) ──────────────────────────────────────
    private String dateOfBirth;      // yyyy-MM-dd string; parsed to LocalDate in service
    private String gender;
    private String maritalStatus;
    private String residentialAddress;
    private String mission;

    /**
     * Lowercase column names that were present in the CSV header for this upload.
     * Used to distinguish "column absent from file" from "column present but blank".
     * Only columns in this set are candidates for update on existing employees.
     */
    private Set<String> presentColumns = new HashSet<>();

    /** Returns true if the given column name (case-insensitive) was in the CSV header. */
    public boolean hasColumn(String columnName) {
        return presentColumns.contains(columnName.toLowerCase());
    }
}
