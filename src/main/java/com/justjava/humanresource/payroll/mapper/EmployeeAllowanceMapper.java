package com.justjava.humanresource.payroll.mapper;


import com.justjava.humanresource.payroll.entity.EmployeeAllowance;
import com.justjava.humanresource.payroll.entity.EmployeeAllowanceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmployeeAllowanceMapper {

    @Mapping(source = "employee.id", target = "employeeId")
    @Mapping(source = "allowance.id", target = "allowanceId")
    EmployeeAllowanceResponse toResponse(EmployeeAllowance entity);
}
