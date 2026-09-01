package com.justjava.humanresource.communication.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
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
@Table(name = "communication_hr_broadcast_attachments")
public class HrBroadcastAttachment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broadcast_id", nullable = false)
    private HrBroadcast broadcast;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false, unique = true)
    private String storedFilename;

    @Column(nullable = false, length = 1000)
    private String storagePath;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(length = 200)
    private String uploadedByEmail;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;
}
