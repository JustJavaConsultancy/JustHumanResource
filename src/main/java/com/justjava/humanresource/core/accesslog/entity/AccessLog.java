package com.justjava.humanresource.core.accesslog.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "access_log")
@Data
public class AccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String method;

    private String endpoint;

    private Integer status;

    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    private Long durationMs;

    private LocalDateTime createdAt = LocalDateTime.now();
}