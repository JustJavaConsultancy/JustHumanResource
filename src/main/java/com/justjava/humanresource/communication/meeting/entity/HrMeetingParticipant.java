package com.justjava.humanresource.communication.meeting.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.hr.entity.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "communication_hr_meeting_participants")
public class HrMeetingParticipant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private HrMeeting meeting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(length = 200)
    private String employeeName;

    @Column(length = 200)
    private String employeeEmail;

    @Column(length = 80)
    private String employeeNumber;

    @Column(nullable = false)
    private boolean notified;

    private LocalDateTime notifiedAt;

    @Column(nullable = false)
    private boolean chatNotified;

    private LocalDateTime chatNotifiedAt;

    @Column(length = 40)
    private String chatStatus;

    @Column(nullable = false)
    private boolean emailNotified;

    private LocalDateTime emailNotifiedAt;

    @Column(length = 40)
    private String emailStatus;

    @Column(length = 1000)
    private String notificationError;
}
