package com.justjava.humanresource.communication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMessageCommand {

    private Long groupId;

    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message must not exceed 5000 characters")
    private String content;
}