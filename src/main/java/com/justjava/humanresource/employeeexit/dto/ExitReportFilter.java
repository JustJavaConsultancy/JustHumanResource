package com.justjava.humanresource.employeeexit.dto;

import com.justjava.humanresource.employeeexit.enums.ExitStatus;
import com.justjava.humanresource.employeeexit.enums.ExitType;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Data
public class ExitReportFilter {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
    private ExitStatus status;
    private ExitType exitType;
    private Long departmentId;
    private String clearanceOwnerGroup;
}
