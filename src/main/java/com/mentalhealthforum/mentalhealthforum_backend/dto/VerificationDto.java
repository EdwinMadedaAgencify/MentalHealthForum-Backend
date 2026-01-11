package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.mentalhealthforum.mentalhealthforum_backend.enums.VerificationType;

import java.util.Map;

public record VerificationDto(
        VerificationType type,  // INVITED or SELF_REG
        String groupPath, // The user's role/group (for onboarding logic)
        VerificationMetadata metadata // Extra info (e.g., username for the login screen)
) {
    public record VerificationMetadata(
            String userId,
            String username,
            String email
    ){};
}
