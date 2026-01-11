package com.mentalhealthforum.mentalhealthforum_backend.dto;

import java.util.UUID;

public record InviterDto (
        UUID userId,
        String displayName
){}


