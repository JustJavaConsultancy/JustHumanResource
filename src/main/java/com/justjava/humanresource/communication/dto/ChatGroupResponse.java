package com.justjava.humanresource.communication.dto;

import com.justjava.humanresource.communication.entity.ChatGroup;
import com.justjava.humanresource.communication.entity.ChatGroupMember;
import com.justjava.humanresource.communication.entity.ChatGroupMemberRole;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatGroupResponse {

    private Long id;
    private String name;
    private String description;
    private String createdByEmail;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isAdmin;
    private boolean isMember;
    private int memberCount;
    private GroupMessageResponse lastMessage;

    public static ChatGroupResponse from(ChatGroup group, Long currentEmployeeId, boolean isAdmin, boolean isMember, int memberCount, GroupMessageResponse lastMessage) {
        ChatGroupResponseBuilder builder = ChatGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .createdByEmail(group.getCreatedByEmail())
                .createdByName(group.getCreatedByName())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .isAdmin(isAdmin)
                .isMember(isMember)
                .memberCount(memberCount)
                .lastMessage(lastMessage);
        return builder.build();
    }
}