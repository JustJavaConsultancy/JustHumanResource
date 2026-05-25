package com.justjava.humanresource.hr.service;

import com.justjava.humanresource.hr.dto.EmployeePayItemUploadDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmployeePayItemCsvParserService {

    public List<EmployeePayItemUploadDTO> parse(MultipartFile file) {
        List<EmployeePayItemUploadDTO> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean skipHeader = true;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                if (skipHeader) {
                    skipHeader = false;
                    continue;
                }

                List<String> parts = parseCsvLine(line);
                EmployeePayItemUploadDTO dto = new EmployeePayItemUploadDTO();
                dto.setRowNumber(lineNumber);
                dto.setEmployeeNumber(value(parts, 0));
                dto.setEmployeeEmail(value(parts, 1));
                dto.setItemType(value(parts, 2));
                dto.setItemCode(value(parts, 3));
                dto.setOverridden(value(parts, 4));

                String overrideAmount = value(parts, 5);
                if (!overrideAmount.isBlank()) {
                    dto.setOverrideAmount(new BigDecimal(overrideAmount));
                }

                String effectiveFrom = value(parts, 6);
                if (!effectiveFrom.isBlank()) {
                    dto.setEffectiveFrom(LocalDate.parse(effectiveFrom));
                }

                String effectiveTo = value(parts, 7);
                if (!effectiveTo.isBlank()) {
                    dto.setEffectiveTo(LocalDate.parse(effectiveTo));
                }

                rows.add(dto);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid pay item CSV file", ex);
        }

        return rows;
    }

    private String value(List<String> parts, int index) {
        if (index >= parts.size() || parts.get(index) == null) {
            return "";
        }
        return parts.get(index).trim();
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }
}
