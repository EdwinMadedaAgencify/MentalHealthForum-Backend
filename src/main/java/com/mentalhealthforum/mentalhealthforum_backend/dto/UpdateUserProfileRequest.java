package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.mentalhealthforum.mentalhealthforum_backend.dto.notification.NotificationPreferences;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ProfileVisibility;
import com.mentalhealthforum.mentalhealthforum_backend.enums.SupportRole;
import com.mentalhealthforum.mentalhealthforum_backend.validation.ValidEmail;
import com.mentalhealthforum.mentalhealthforum_backend.validation.firstName.ValidFirstName;
import com.mentalhealthforum.mentalhealthforum_backend.validation.lastName.ValidLastName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record UpdateUserProfileRequest(

        @ValidEmail
        String email,

        @ValidFirstName
        String firstName,

        @ValidLastName
        String lastName,

        @Size(max = 500, message = "Bio cannot exceed 500 characters.")
        String bio,

        @Size(max = 100, message = "Display name cannot exceed 100 characters.")
        @Pattern(regexp = "^[\\p{L}0-9 .'-]+$",
                message = "Display name can only contain letters, numbers, spaces, dots, apostrophes, and hyphens")
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
