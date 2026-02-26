package com.justjava.humanresource.hr.mapper;

import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.entity.*;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public abstract class EmployeeMapper {

    @Autowired
    DepartmentRepository departmentRepository;
    @Autowired
    JobStepRepository jobStepRepository;
    @Autowired
    PayGroupRepository payGroupRepository;

    /* ============================================================
       DTO → ENTITY (including personal fields)
       ============================================================ */
    @Mapping(target = "department", expression = "java(resolveDepartment(dto.getDepartmentId()))")
    @Mapping(target = "jobStep", expression = "java(resolveJobStep(dto.getJobStepId()))")
    @Mapping(target = "payGroup", expression = "java(resolvePayGroup(dto.getPayGroupId()))")
    // emergencyContact is intentionally NOT mapped – handled in service
    // personal fields are mapped directly
    public abstract Employee toEntity(EmployeeDTO dto);

    /* ============================================================
       ENTITY → DTO (including all fields)
       ============================================================ */
    @Mapping(source = "department.id", target = "departmentId")
    @Mapping(source = "jobStep.id", target = "jobStepId")
    @Mapping(source = "payGroup.id", target = "payGroupId")
    @Mapping(source = "emergencyContact.contactName", target = "emergencyContactName")
    @Mapping(source = "emergencyContact.relationship", target = "emergencyRelationship")
    @Mapping(source = "emergencyContact.phoneNumber", target = "emergencyPhoneNumber")
    @Mapping(source = "emergencyContact.alternativePhoneNumber", target = "emergencyAlternativePhoneNumber")
    // personal fields
    @Mapping(source = "dateOfBirth", target = "dateOfBirth")
    @Mapping(source = "gender", target = "gender")
    @Mapping(source = "maritalStatus", target = "maritalStatus")
    @Mapping(source = "residentialAddress", target = "residentialAddress")
    @Mapping(source = "mission", target = "mission")
    public abstract EmployeeDTO toDto(Employee employee);

    /* ============================================================
       INTERNAL RESOLUTION
       ============================================================ */
    protected Department resolveDepartment(Long id) {
        if (id == null) return null;
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
    }

    protected JobStep resolveJobStep(Long id) {
        if (id == null) return null;
        return jobStepRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("JobStep", id));
    }

    protected PayGroup resolvePayGroup(Long id) {
        if (id == null) return null;
        return payGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PayGroup", id));
    }

    public List<EmployeeDTO> toDtoList(List<Employee> employees) {
        return employees.stream().map(this::toDto).toList();
    }
}