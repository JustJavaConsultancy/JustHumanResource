package com.justjava.humanresource.hr.service;

import com.justjava.humanresource.hr.dto.EmployeeUploadDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.*;

/**
 * Parses an employee CSV file using header-driven column detection.
 *
 * <p>The first row must be a header row containing column names (case-insensitive,
 * spaces stripped). Only columns whose names appear in the header are populated on
 * each {@link EmployeeUploadDTO}; absent columns are never touched, allowing the
 * upload service to distinguish "not in file" from "blank value".</p>
 *
 * <p>Supported column names (case-insensitive, spaces ignored):</p>
 * <ul>
 *   <li>firstName, secondName (alias: lastName), email, grade, gross</li>
 *   <li>accountName, bankName, accountNumber</li>
 *   <li>tinNumber, rsaPin, pfa, ninNumber, bvnNumber</li>
 *   <li>phoneNumber, dateOfHire (yyyy-MM-dd)</li>
 *   <li>nextOfKinName, nextOfKinPhoneNumber, nextOfKinEmail, nextOfKinAddress</li>
 *   <li>guarantorName, guarantorPhoneNumber, guarantorEmail, guarantorAddress, guarantorNinNumber</li>
 *   <li>dateOfBirth (yyyy-MM-dd), gender, maritalStatus, residentialAddress, mission</li>
 * </ul>
 */
@Service
public class CsvParserService {

    public List<EmployeeUploadDTO> parse(MultipartFile file) {

        List<EmployeeUploadDTO> list = new ArrayList<>();

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(file.getInputStream()))) {

            String headerLine = reader.readLine();
            if (headerLine == null) return list;

            // Build column-name → index map from the header row.
            // Normalise: lowercase + strip all whitespace. "lastname" is aliased to "secondname".
            String[] headers = headerLine.split(",", -1);
            Map<String, Integer> columnIndex = new LinkedHashMap<>();
            Set<String> presentColumns = new LinkedHashSet<>();

            for (int i = 0; i < headers.length; i++) {
                String normalized = normalize(headers[i]);
                if (normalized.equals("lastname")) normalized = "secondname"; // alias
                columnIndex.put(normalized, i);
                presentColumns.add(normalized);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                String[] parts = line.split(",", -1);
                EmployeeUploadDTO dto = new EmployeeUploadDTO();
                dto.setPresentColumns(new HashSet<>(presentColumns));

                get(parts, columnIndex, "email").ifPresent(dto::setEmail);
                get(parts, columnIndex, "firstname").ifPresent(dto::setFirstName);
                get(parts, columnIndex, "secondname").ifPresent(dto::setSecondName);
                get(parts, columnIndex, "grade").ifPresent(dto::setGrade);
                get(parts, columnIndex, "gross").map(BigDecimal::new).ifPresent(dto::setGross);

                get(parts, columnIndex, "accountname").ifPresent(dto::setAccountName);
                get(parts, columnIndex, "bankname").ifPresent(dto::setBankName);
                get(parts, columnIndex, "accountnumber").ifPresent(dto::setAccountNumber);

                get(parts, columnIndex, "tinnumber").ifPresent(dto::setTinNumber);
                get(parts, columnIndex, "rsapin").ifPresent(dto::setRsaPin);
                get(parts, columnIndex, "pfa").ifPresent(dto::setPfa);
                get(parts, columnIndex, "ninnumber").ifPresent(dto::setNinNumber);
                get(parts, columnIndex, "bvnnumber").ifPresent(dto::setBvnNumber);

                get(parts, columnIndex, "phonenumber").ifPresent(dto::setPhoneNumber);
                get(parts, columnIndex, "dateofhire").ifPresent(dto::setDateOfHire);

                get(parts, columnIndex, "nextofkinname").ifPresent(dto::setNextOfKinName);
                get(parts, columnIndex, "nextofkinphonenumber").ifPresent(dto::setNextOfKinPhoneNumber);
                get(parts, columnIndex, "nextofkinemail").ifPresent(dto::setNextOfKinEmail);
                get(parts, columnIndex, "nextofkinaddress").ifPresent(dto::setNextOfKinAddress);

                get(parts, columnIndex, "guarantorname").ifPresent(dto::setGuarantorName);
                get(parts, columnIndex, "guarantorphonenumber").ifPresent(dto::setGuarantorPhoneNumber);
                get(parts, columnIndex, "guarantoremail").ifPresent(dto::setGuarantorEmail);
                get(parts, columnIndex, "guarantoraddress").ifPresent(dto::setGuarantorAddress);
                get(parts, columnIndex, "guarantorninnumber").ifPresent(dto::setGuarantorNinNumber);

                get(parts, columnIndex, "dateofbirth").ifPresent(dto::setDateOfBirth);
                get(parts, columnIndex, "gender").ifPresent(dto::setGender);
                get(parts, columnIndex, "maritalstatus").ifPresent(dto::setMaritalStatus);
                get(parts, columnIndex, "residentialaddress").ifPresent(dto::setResidentialAddress);
                get(parts, columnIndex, "mission").ifPresent(dto::setMission);

                list.add(dto);
            }

        } catch (Exception ex) {
            throw new IllegalStateException("Invalid CSV file", ex);
        }

        return list;
    }

    /**
     * Returns the trimmed, non-blank cell value at {@code columnName}'s position,
     * or {@link Optional#empty()} if the column is absent from the header or the
     * cell is blank.
     */
    private Optional<String> get(String[] parts, Map<String, Integer> columnIndex, String columnName) {
        Integer idx = columnIndex.get(columnName);
        if (idx == null || idx >= parts.length) return Optional.empty();
        String value = parts[idx].trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    /** Lowercase + strip all whitespace. */
    private String normalize(String raw) {
        return raw.trim().toLowerCase().replaceAll("\\s+", "");
    }
}
