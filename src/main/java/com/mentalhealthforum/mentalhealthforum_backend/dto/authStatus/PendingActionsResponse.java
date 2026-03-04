package com.mentalhealthforum.mentalhealthforum_backend.dto.authStatus;

import java.util.List;

public record PendingActionsResponse(
        String identifier,
        List<String> requiredActions,
        List<String> instructions
) {}
