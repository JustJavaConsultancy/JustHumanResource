package com.justjava.humanresource.communication.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.hr.entity.Department;
import com.justjava.humanresource.hr.entity.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "communication_chat_groups")
public class ChatGroup extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_employee_id")
    private Employee createdByEmployee;

    @Column(length = 200)
    private String createdByEmail;

    @Column(length = 40)
    private String createdByRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatGroupStatus status = ChatGroupStatus.ACTIVE;

    private LocalDateTime lastMessageAt;
}
