package com.mentalhealthforum.mentalhealthforum_backend.dto;

/**
 * DTO for admin user creation responses.
 * Contains information needed for admin to complete the user setup process.
 */
public record AdminCreateUserResponse (
        /*
          Keycloak user ID of the newly created user.
         */
        String userId,

        /*
          Username assigned to the user (auto-generated or provided).
         */
        String username,

        /*
          Auto-generated temporary password for the user.
          IMPORTANT: This is returned ONLY ONCE in the API response and never stored.
          Admin should provide this to the user through secure channel.
         */
        String temporaryPassword,

        /*
          Invitation link for the user to complete onboarding.
          Contains secure token for authentication without password.
          Admin can manually send this link if email sending fails.
         */
        String invitationLink,

        /*
          Whether invitation email was successfully sent.
          Initially false until Novu integration is implemented.
         */
        boolean emailSent
){};
