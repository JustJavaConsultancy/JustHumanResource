package com.justjava.humanresource.payroll.mapper;

import com.justjava.humanresource.payroll.entity.PayGroupDeduction;
import com.justjava.humanresource.payroll.entity.PayGroupDeductionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PayGroupDeductionMapper {

    @Mapping(source = "payGroup.id", target = "payGroupId")
    @Mapping(source = "deduction.id", target = "deductionId")
    PayGroupDeductionResponse toResponse(PayGroupDeduction entity);
}
