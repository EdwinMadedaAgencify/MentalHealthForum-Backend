package com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.adminUser;

import java.util.UUID;

public record InviterDto (
        UUID userId,
        String displayName
){}


