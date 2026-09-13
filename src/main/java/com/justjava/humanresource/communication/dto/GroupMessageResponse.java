package com.justjava.humanresource.communication.dto;

import com.justjava.humanresource.communication.entity.GroupChatMessage;
import com.justjava.humanresource.hr.entity.Employee;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMessageResponse {

    private Long id;
    private Long groupId;
    private Long senderId;
    private String senderName;
    private String senderEmail;
    private String content;
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private List<GroupMessageAttachmentResponse> attachments;

    public static GroupMessageResponse from(GroupChatMessage message) {
        Employee sender = message.getSender();
        return GroupMessageResponse.builder()
                .id(message.getId())
                .groupId(message.getGroup().getId())
                .senderId(sender.getId())
                .senderName(sender.getFirstName() + " " + sender.getLastName())
                .senderEmail(sender.getEmail())
                .content(message.getContent())
                .deliveredAt(message.getDeliveredAt())
                .readAt(message.getReadAt())
                .createdAt(message.getCreatedAt())
                .build();
    }
}