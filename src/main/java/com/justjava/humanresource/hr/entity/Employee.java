package com.justjava.humanresource.hr.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.core.enums.EmploymentStatus;
import com.justjava.humanresource.core.enums.RecordStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "employees")
public class Employee extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String employeeNumber;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String email;

    private String phoneNumber;

    private LocalDate dateOfHire;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentStatus employmentStatus;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

    @ManyToOne(optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(optional = false)
    @JoinColumn(name = "job_step_id")
    private JobStep jobStep;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pay_group_id")
    private PayGroup payGroup;

    @OneToMany(mappedBy = "employee", fetch = FetchType.LAZY)
    private List<EmployeeBankDetail> bankDetails;

    private boolean payrollEnabled;
    private boolean kpiEnabled;

    @Column(updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime activatedAt;

    // ---------- Emergency contact ----------
    @OneToOne(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private EmergencyContact emergencyContact;

    public void setEmergencyContact(EmergencyContact contact) {
        this.emergencyContact = contact;
        if (contact != null) {
            contact.setEmployee(this);
        }
    }

    // ---------- Personal information (employee-editable) ----------
    private LocalDate dateOfBirth;
    private String gender;
    private String maritalStatus;
    private String residentialAddress;

    // New mission field
    @Column(columnDefinition = "TEXT")
    private String mission;

    // -------------------------------------------------------------

    public String getFullName() {
        return firstName + " " + lastName;
    }
}