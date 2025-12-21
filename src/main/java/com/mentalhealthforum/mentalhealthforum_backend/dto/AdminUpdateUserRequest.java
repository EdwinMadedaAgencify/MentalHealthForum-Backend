package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;
import com.mentalhealthforum.mentalhealthforum_backend.validation.group.ValidAssignableGroup;

public record AdminUpdateUserRequest(
        @ValidAssignableGroup
        GroupPath group,
        Boolean isEnabled
) {}
