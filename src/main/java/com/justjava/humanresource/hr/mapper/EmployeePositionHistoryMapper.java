package com.justjava.humanresource.hr.mapper;


import com.justjava.humanresource.hr.dto.EmployeePositionHistoryDTO;
import com.justjava.humanresource.hr.entity.EmployeePositionHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface EmployeePositionHistoryMapper {

    @Mapping(target = "employeeId", source = "employee.id")
    @Mapping(target = "employeeNumber", source = "employee.employeeNumber")
    @Mapping(target = "employeeName",
            expression = "java(entity.getEmployee().getFirstName() + \" \" + entity.getEmployee().getLastName())")

    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")

    @Mapping(target = "jobStepId", source = "jobStep.id")
    @Mapping(target = "jobStepName", source = "jobStep.name")

    @Mapping(target = "payGroupId", source = "payGroup.id")
    @Mapping(target = "payGroupName", source = "payGroup.name")

    EmployeePositionHistoryDTO toDto(EmployeePositionHistory entity);
}
