package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;
import com.mentalhealthforum.mentalhealthforum_backend.validation.ValidEmail;
import com.mentalhealthforum.mentalhealthforum_backend.validation.firstName.ValidFirstName;
import com.mentalhealthforum.mentalhealthforum_backend.validation.group.ValidAssignableGroup;
import com.mentalhealthforum.mentalhealthforum_backend.validation.lastName.ValidLastName;
import jakarta.validation.constraints.NotBlank;

public record ReissueInvitationRequest(
        @NotBlank(message = "Email is required")
        @ValidEmail
        String email,

        @ValidAssignableGroup
        GroupPath group,

        boolean sendInvitationEmail
) {}
