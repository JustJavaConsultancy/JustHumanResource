package com.justjava.humanresource.communication.entity;

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

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "communication_hr_broadcasts")
public class HrBroadcast extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String title;

    @Lob
    @Column(nullable = false, length = 5000)
    private String content;

    @Column(length = 160)
    private String createdByName;

    @Column(length = 200)
    private String createdByEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HrBroadcastStatus status = HrBroadcastStatus.ACTIVE;
}
