package com.justjava.humanresource.hr.service.impl;

import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.core.exception.ResourceNotFoundException;
import com.justjava.humanresource.dispatcher.PayrollMessageDispatcher;
import com.justjava.humanresource.hr.dto.EmployeeDTO;
import com.justjava.humanresource.hr.entity.*;
import com.justjava.humanresource.hr.mapper.EmployeeMapper;
import com.justjava.humanresource.hr.repository.EmployeePositionHistoryRepository;
import com.justjava.humanresource.hr.repository.EmployeeRepository;
import com.justjava.humanresource.hr.repository.JobStepRepository;
import com.justjava.humanresource.hr.repository.PayGroupRepository;
import com.justjava.humanresource.hr.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final JobStepRepository jobStepRepository;
    private final PayrollMessageDispatcher payrollMessageDispatcher;
    private final EmployeeMapper employeeMapper;
    private final PayGroupRepository payGroupRepository;
    private final EmployeePositionHistoryRepository employeePositionHistoryRepository;

    /* =========================
     * EXISTING METHODS (unchanged)
     * ========================= */
    @Override
    public Employee createEmployee(EmployeeDTO dto) {
        Employee employee = employeeMapper.toEntity(dto);
        if (hasEmergencyContactData(dto)) {
            EmergencyContact contact = EmergencyContact.builder()
                    .contactName(dto.getEmergencyContactName())
                    .relationship(dto.getEmergencyRelationship())
                    .phoneNumber(dto.getEmergencyPhoneNumber())
                    .alternativePhoneNumber(dto.getEmergencyAlternativePhoneNumber())
                    .build();
            employee.setEmergencyContact(contact);
        }
        return employeeRepository.save(employee);
    }

    @Override
    public List<EmployeeDTO> getAllEmployees() {
        List<Employee> employees = employeeRepository.findAll();
        return employeeMapper.toDtoList(employees);
    }

    public EmployeeDTO createAndActivateEmployee(EmployeeDTO dto) {
        Employee employee = createEmployee(dto);
        return changeEmploymentStatus(employee.getId(), EmploymentStatus.ACTIVE, LocalDate.now());
    }

    @Override
    public Employee getByEmployeeNumber(String employeeNumber) {
        return employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeNumber));
    }

    @Override
    public Employee getByEmail(String email) {
        return employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", email));
    }

    /* =========================
     * REFINED, INTENT-BASED METHODS
     * ========================= */

    @Override
    public EmployeeDTO changePayGroup(Long employeeId, PayGroup newPayGroup, LocalDate effectiveDate) {
        Employee employee = getById(employeeId);
        employee.setPayGroup(newPayGroup);
        Employee saved = employeeRepository.save(employee);
        payrollMessageDispatcher.requestPayroll(saved.getId(), effectiveDate);
        return employeeMapper.toDto(saved);
    }

    @Override
    public EmployeeDTO changeJobStep(Long employeeId, Long newJobStepId, LocalDate effectiveDate) {
        Employee employee = getById(employeeId);
        JobStep newJobStep = jobStepRepository.findById(newJobStepId)
                .orElseThrow(() -> new ResourceNotFoundException("JobStep", newJobStepId));
        employee.setJobStep(newJobStep);
        Employee saved = employeeRepository.save(employee);
        payrollMessageDispatcher.requestPayroll(saved.getId(), effectiveDate);
        return employeeMapper.toDto(saved);
    }

    @Transactional
    public void changePosition(Long employeeId, Long jobStepId, Long payGroupId, LocalDate effectiveFrom) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        JobStep jobStep = jobStepRepository.findById(jobStepId).orElseThrow();
        PayGroup payGroup = payGroupRepository.findById(payGroupId).orElseThrow();

        Optional<EmployeePositionHistory> current = employeePositionHistoryRepository.resolvePosition(
                employeeId, effectiveFrom.minusDays(1), RecordStatus.ACTIVE);
        current.ifPresent(existing -> {
            existing.setEffectiveTo(effectiveFrom.minusDays(1));
            existing.setStatus(RecordStatus.INACTIVE);
            employeePositionHistoryRepository.save(existing);
        });

        EmployeePositionHistory newPosition = new EmployeePositionHistory();
        newPosition.setEmployee(employee);
        newPosition.setJobStep(jobStep);
        newPosition.setPayGroup(payGroup);
        newPosition.setEffectiveFrom(effectiveFrom);
        newPosition.setStatus(RecordStatus.ACTIVE);
        employeePositionHistoryRepository.save(newPosition);

        payrollMessageDispatcher.requestPayroll(employeeId, effectiveFrom);
    }

    @Override
    public EmployeeDTO changeEmploymentStatus(Long employeeId, EmploymentStatus newStatus, LocalDate effectiveDate) {
        Employee employee = getById(employeeId);
        employee.setEmploymentStatus(newStatus);
        Employee saved = employeeRepository.save(employee);
        payrollMessageDispatcher.requestPayroll(saved.getId(), effectiveDate);
        return employeeMapper.toDto(saved);
    }

    /* =========================
     * EMERGENCY CONTACT UPDATE
     * ========================= */
    @Transactional
    @Override
    public EmployeeDTO updateEmployee(Long id, EmployeeDTO dto) {
        Employee existing = getById(id);

        // Only update emergency contact
        if (hasEmergencyContactData(dto)) {
            EmergencyContact contact = existing.getEmergencyContact();
            if (contact == null) {
                contact = new EmergencyContact();
                contact.setEmployee(existing);
                existing.setEmergencyContact(contact);
            }
            contact.setContactName(dto.getEmergencyContactName());
            contact.setRelationship(dto.getEmergencyRelationship());
            contact.setPhoneNumber(dto.getEmergencyPhoneNumber());
            contact.setAlternativePhoneNumber(dto.getEmergencyAlternativePhoneNumber());
        } else {
            existing.setEmergencyContact(null);
        }

        Employee saved = employeeRepository.save(existing);
        return employeeMapper.toDto(saved);
    }

    /* =========================
     * NEW: PERSONAL INFORMATION UPDATE
     * ========================= */
    @Transactional
    @Override
    public EmployeeDTO updatePersonalInfo(Long id, EmployeeDTO dto) {
        Employee existing = getById(id);

        // Update only the personal fields (employee-editable)
        existing.setDateOfBirth(dto.getDateOfBirth());
        existing.setGender(dto.getGender());
        existing.setMaritalStatus(dto.getMaritalStatus());
        existing.setResidentialAddress(dto.getResidentialAddress());
        existing.setMission(dto.getMission());

        Employee saved = employeeRepository.save(existing);
        return employeeMapper.toDto(saved);
    }

    @Override
    public String generateInitialPassword(Employee employee) {
        return "password!";
    }

    /* =========================
     * INTERNAL HELPERS
     * ========================= */
    private Employee getById(Long employeeId) {


    @Override
    public Employee getById(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
    }

    private boolean hasEmergencyContactData(EmployeeDTO dto) {
        return dto.getEmergencyContactName() != null && !dto.getEmergencyContactName().trim().isEmpty()
                || dto.getEmergencyRelationship() != null && !dto.getEmergencyRelationship().trim().isEmpty()
                || dto.getEmergencyPhoneNumber() != null && !dto.getEmergencyPhoneNumber().trim().isEmpty()
                || dto.getEmergencyAlternativePhoneNumber() != null && !dto.getEmergencyAlternativePhoneNumber().trim().isEmpty();
    }

    @Override
    public Employee save(Employee employee) {
        return employeeRepository.save(employee);
    }
}