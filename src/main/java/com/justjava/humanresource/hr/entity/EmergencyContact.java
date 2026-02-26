package com.justjava.humanresource.hr.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "emergency_contacts")
public class EmergencyContact extends BaseEntity {

    @Column(nullable = false)
    private String contactName;

    @Column(nullable = false)
    private String relationship;

    private String phoneNumber;

    private String alternativePhoneNumber;

    @OneToOne
    @JoinColumn(name = "employee_id", unique = true)
    private Employee employee;
}