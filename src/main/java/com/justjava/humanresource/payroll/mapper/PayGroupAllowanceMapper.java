package com.justjava.humanresource.payroll.mapper;

import com.justjava.humanresource.payroll.entity.PayGroupAllowance;
import com.justjava.humanresource.payroll.entity.PayGroupAllowanceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PayGroupAllowanceMapper {

    @Mapping(source = "payGroup.id", target = "payGroupId")
    @Mapping(source = "allowance.id", target = "allowanceId")
    PayGroupAllowanceResponse toResponse(PayGroupAllowance entity);
}