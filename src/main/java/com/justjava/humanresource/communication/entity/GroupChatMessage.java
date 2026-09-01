package com.justjava.humanresource.communication.entity;

import com.justjava.humanresource.core.entity.BaseEntity;
import com.justjava.humanresource.hr.entity.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "communication_group_chat_messages")
public class GroupChatMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_group_id", nullable = false)
    private ChatGroup chatGroup;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private Employee sender;

    @Lob
    @Column(nullable = false, length = 2000)
    private String content;
}
