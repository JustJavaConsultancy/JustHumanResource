package com.justjava.humanresource.hr.service;

import com.justjava.humanresource.hr.dto.EmployeeUploadDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvParserService {

    public List<EmployeeUploadDTO> parse(MultipartFile file) {

        List<EmployeeUploadDTO> list = new ArrayList<>();

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(file.getInputStream()))) {

            String line;
            boolean skipHeader = true;

            while ((line = reader.readLine()) != null) {

                if (skipHeader) {
                    skipHeader = false;
                    continue;
                }

                String[] parts = line.split(",");

                EmployeeUploadDTO dto = new EmployeeUploadDTO();
                dto.setFirstName(parts[0].trim());
                dto.setSecondName(parts[1].trim());
                dto.setEmail(parts[2].trim());
                dto.setGrade(parts[3].trim());
                dto.setGross(new BigDecimal(parts[4].trim()));


                list.add(dto);
            }

        } catch (Exception ex) {
            throw new IllegalStateException("Invalid CSV file", ex);
        }

        return list;
    }
}