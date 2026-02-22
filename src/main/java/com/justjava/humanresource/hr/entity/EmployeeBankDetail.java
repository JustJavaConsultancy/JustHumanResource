package com.justjava.humanresource.hr.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.core.enums.RecordStatus;
import com.justjava.humanresource.hr.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "employee_bank_details",
        indexes = {
                @Index(name = "idx_bank_employee", columnList = "employee_id"),
                @Index(name = "idx_bank_effective", columnList = "effective_from, effective_to")
        })
public class EmployeeBankDetail extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false)
    private String bankName;

    @Column(nullable = false)
    private String accountName;

    @Column(nullable = false, length = 20)
    private String accountNumber;

    @Column(length = 15)
    private String sortCode;

    @Column(length = 15)
    private String branchCode;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status = RecordStatus.ACTIVE;

    @Column(nullable = false)
    private boolean primaryAccount;
}