package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;
import com.mentalhealthforum.mentalhealthforum_backend.validation.ValidEmail;
import com.mentalhealthforum.mentalhealthforum_backend.validation.firstName.ValidFirstName;
import com.mentalhealthforum.mentalhealthforum_backend.validation.group.ValidAssignableGroup;
import com.mentalhealthforum.mentalhealthforum_backend.validation.lastName.ValidLastName;
import com.mentalhealthforum.mentalhealthforum_backend.validation.username.ValidUsername;
import jakarta.validation.constraints.*;

/**
 * DTO for admin user creation requests.
 * Differs from self-registration by:
 * - No password/confirmPassword fields (admin doesn't know user's password)
 * - Auto-generates username if not provided
 * - Requires explicit group assignment
 * - Supports pending actions for onboarding workflow
 */
public record AdminCreateUserRequest (

        @NotBlank(message = "Email is required.")
        @ValidEmail
        String email,

        @NotBlank(message = "First name is required.")
        @ValidFirstName
        String firstName,

        @NotBlank(message = "Last name is required.")
        @ValidLastName
        String lastName,

        /*
          Optional username. If not provided, will be auto-generated from first and last name.
          Format: names.lastname.randomDigits (e.g., john.doe.123)
         */
       @ValidUsername
        String username,

        /*
          Required group assignment. Admin must specify which group the user belongs to.
          Must be an assignable group (leaf group with role grants).
         */
        @ValidAssignableGroup
        GroupPath group,

        /*
          Whether to send invitation email to the user.
          If true, system will generate invitation token and send email (requires Novu integration).
          If false, admin must manually provide invitation link/temporary password.
         */
        boolean sendInvitationEmail

){}
