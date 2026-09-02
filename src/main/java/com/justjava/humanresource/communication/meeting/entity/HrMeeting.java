package com.justjava.humanresource.communication.meeting.entity;

import com.justjava.humanresource.communication.meeting.MeetingProvider;
import com.justjava.humanresource.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "communication_hr_meetings")
public class HrMeeting extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MeetingProvider provider;

    @Column(nullable = false, length = 200)
    private String subject;

    @Lob
    @Column(length = 2000)
    private String agenda;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private boolean instantMeeting;

    @Column(length = 300)
    private String externalMeetingId;

    @Column(length = 1000)
    private String joinUrl;

    @Column(length = 2000)
    private String hostUrl;

    @Column(length = 160)
    private String createdByName;

    @Column(length = 200)
    private String createdByEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HrMeetingStatus status = HrMeetingStatus.SCHEDULED;

    @Lob
    private String providerResponseSummary;
}
