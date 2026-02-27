package com.justjava.humanresource.hr.mapper;

import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.entity.*;
import com.justjava.humanresource.hr.repository.DepartmentRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

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
       DTO → ENTITY (bankDetails intentionally ignored)
       ============================================================ */
    @Mapping(target = "department", expression = "java(resolveDepartment(dto.getDepartmentId()))")
    @Mapping(target = "jobStep", expression = "java(resolveJobStep(dto.getJobStepId()))")
    @Mapping(target = "payGroup", expression = "java(resolvePayGroup(dto.getPayGroupId()))")
    public abstract Employee toEntity(EmployeeDTO dto);

    /* ============================================================
       ENTITY → DTO (including emergency contact and bank details)
       ============================================================ */
    @Mapping(source = "department.id", target = "departmentId")
    @Mapping(source = "jobStep.id", target = "jobStepId")
    @Mapping(source = "payGroup.id", target = "payGroupId")
    // Emergency contact
    @Mapping(source = "emergencyContact.contactName", target = "emergencyContactName")
    @Mapping(source = "emergencyContact.relationship", target = "emergencyRelationship")
    @Mapping(source = "emergencyContact.phoneNumber", target = "emergencyPhoneNumber")
    @Mapping(source = "emergencyContact.alternativePhoneNumber", target = "emergencyAlternativePhoneNumber")
    // Personal fields
    @Mapping(source = "dateOfBirth", target = "dateOfBirth")
    @Mapping(source = "gender", target = "gender")
    @Mapping(source = "maritalStatus", target = "maritalStatus")
    @Mapping(source = "residentialAddress", target = "residentialAddress")
    @Mapping(source = "mission", target = "mission")
    // Bank details – use custom methods with correct string literals
    @Mapping(target = "bankName", expression = "java(getActiveBankField(employee, \"bankName\"))")
    @Mapping(target = "accountName", expression = "java(getActiveBankField(employee, \"accountName\"))")
    @Mapping(target = "accountNumber", expression = "java(getActiveBankField(employee, \"accountNumber\"))")
    @Mapping(target = "bankDetailId", expression = "java(getActiveBankId(employee))")
    public abstract EmployeeDTO toDto(Employee employee);

    /* ============================================================
       Custom methods to extract active bank detail fields
       ============================================================ */
    protected String getActiveBankField(Employee employee, String fieldName) {
        if (employee.getBankDetails() == null) return null;
        Optional<EmployeeBankDetail> active = employee.getBankDetails().stream()
                .filter(b -> b.getStatus() == RecordStatus.ACTIVE && (b.isPrimaryAccount() || b.getEffectiveTo() == null))
                .findFirst();
        if (active.isPresent()) {
            EmployeeBankDetail bank = active.get();
            switch (fieldName) {
                case "bankName": return bank.getBankName();
                case "accountName": return bank.getAccountName();
                case "accountNumber": return bank.getAccountNumber();
                default: return null;
            }
        }
        return null;
    }

    protected Long getActiveBankId(Employee employee) {
        if (employee.getBankDetails() == null) return null;
        return employee.getBankDetails().stream()
                .filter(b -> b.getStatus() == RecordStatus.ACTIVE && (b.isPrimaryAccount() || b.getEffectiveTo() == null))
                .findFirst()
                .map(EmployeeBankDetail::getId)
                .orElse(null);
    }

    /* ============================================================
       Resolution methods
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