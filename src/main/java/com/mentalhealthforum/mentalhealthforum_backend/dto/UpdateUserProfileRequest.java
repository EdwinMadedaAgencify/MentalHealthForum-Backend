package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.mentalhealthforum.mentalhealthforum_backend.dto.notification.NotificationPreferences;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ProfileVisibility;
import com.mentalhealthforum.mentalhealthforum_backend.enums.SupportRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UpdateUserProfileRequest(

        @Email(message = "Email must be a valid email format.")
        @Size(max = 100, message = "Email cannot exceed 100 characters.")
        String email,

        @Size(max = 50, message = "First name cannot exceed 50 characters.")
        String firstName,

        @Size(max = 50, message = "Last name cannot exceed 50 characters.")
        String lastName,

        @Size(max = 500, message = "Bio cannot exceed 500 characters.")
        String bio,

        @Size(max = 100, message = "Display name cannot exceed 100 characters.")
        String displayName,

        @Size(max = 255)
        @URL(message = "Avatar URL must be a valid URL")
        String avatarUrl,

        String timezone,

        ProfileVisibility profileVisibility,

        SupportRole supportRole,

        @Valid
        NotificationPreferences notificationPreferences
) {}
