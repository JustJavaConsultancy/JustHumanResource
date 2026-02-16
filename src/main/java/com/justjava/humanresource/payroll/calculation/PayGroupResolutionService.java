package com.justjava.humanresource.payroll.calculation;


import com.justjava.humanresource.hr.entity.Employee;
import com.justjava.humanresource.hr.entity.PayGroup;
import com.justjava.humanresource.payroll.calculation.dto.ResolvedPayComponents;

import java.time.LocalDate;

public interface PayGroupResolutionService {

    ResolvedPayComponents resolve(
            PayGroup payGroup,
            Employee employee,
            LocalDate payrollDate
    );
}

