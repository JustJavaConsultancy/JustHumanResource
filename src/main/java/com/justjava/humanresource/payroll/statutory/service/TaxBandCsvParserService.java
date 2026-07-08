package com.justjava.humanresource.payroll.statutory.service;

import com.justjava.humanresource.payroll.statutory.dto.TaxBandUploadRowDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaxBandCsvParserService {

    public List<TaxBandUploadRowDTO> parse(MultipartFile file) {
        List<TaxBandUploadRowDTO> rows = new ArrayList<>();

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
                TaxBandUploadRowDTO dto = new TaxBandUploadRowDTO();
                dto.setRowNumber(lineNumber);

                String lower = value(parts, 0);
                String upper = value(parts, 1);
                String rate  = value(parts, 2);

                if (!lower.isBlank()) {
                    dto.setLowerBound(new BigDecimal(lower));
                }
                if (!upper.isBlank()) {
                    dto.setUpperBound(new BigDecimal(upper));
                }
                if (!rate.isBlank()) {
                    dto.setRate(new BigDecimal(rate));
                }

                rows.add(dto);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid tax band CSV file", ex);
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