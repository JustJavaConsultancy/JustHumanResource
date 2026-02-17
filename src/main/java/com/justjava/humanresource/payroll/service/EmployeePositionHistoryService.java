package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.hr.dto.EmployeePositionHistoryDTO;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;

import java.time.LocalDate;
import java.util.List;

public interface EmployeePositionHistoryService {

    EmployeePositionHistory createInitialPosition(Long employeeId);

    EmployeePositionHistory changePosition(
            Long employeeId,
            Long departmentId,
            Long jobStepId,
            Long payGroupId,
            LocalDate effectiveDate
    );

    List<EmployeePositionHistoryDTO> getActivePositions();

    EmployeePositionHistory getCurrentPosition(Long employeeId);
    EmployeePositionHistoryDTO getCurrentPositionAPI(Long employeeId);

}
