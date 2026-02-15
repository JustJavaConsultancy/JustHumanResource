package com.justjava.humanresource.payroll.mapper;

import com.justjava.humanresource.payroll.entity.EmployeeDeduction;
import com.justjava.humanresource.payroll.entity.EmployeeDeductionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmployeeDeductionMapper {

    @Mapping(source = "employee.id", target = "employeeId")
    @Mapping(source = "deduction.id", target = "deductionId")
    EmployeeDeductionResponse toResponse(EmployeeDeduction entity);
}
