package com.justjava.humanresource.payroll.service;

import com.justjava.humanresource.payroll.entity.PaySlipDTO;

import java.time.YearMonth;
import java.util.List;

public interface PaySlipService {

    void generatePaySlip(Long payrollRunId);
    List<PaySlipDTO> getPaySlipsByEmployee(Long employeeId);

    List<PaySlipDTO> getPaySlipsForPeriod(Long companyId, Long periodId);

    List<PaySlipDTO> getEmployeePaySlipsForPeriod(
            Long companyId,
            Long employeeId,
            Long periodId
    );
    PaySlipDTO getLatestPaySlipForEmployeeForPeriod(
            Long companyId,
            Long employeeId,
            Long periodId
    );
    List<PaySlipDTO> getLatestPaySlipsForPeriod(
            Long companyId,
            Long periodId
    );
    public List<PaySlipDTO> getCurrentPeriodPaySlips(Long companyId);
    List<PaySlipDTO> getAllClosedPeriodPaySlips(Long companyId);
    //public List<PaySlipDTO> getEmployeePaySlips(Long employeeId);
    PaySlipDTO getCurrentPeriodPaySlipForEmployee(Long companyId, Long employeeId);
    PaySlipDTO getLatestClosedPeriodPaySlipForEmployee(Long companyId, Long employeeId);
}
