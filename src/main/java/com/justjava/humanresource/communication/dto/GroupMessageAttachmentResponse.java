package com.justjava.humanresource.communication.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMessageAttachmentResponse {

    private Long id;
    private String originalFilename;
    private String storedFilename;
    private String storagePath;
    private String contentType;
    private Long fileSize;
    private Long uploadedByEmployeeId;
    private String uploadedByName;
    private LocalDateTime uploadedAt;
}