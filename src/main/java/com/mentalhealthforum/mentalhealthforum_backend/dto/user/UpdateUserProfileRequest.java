package com.mentalhealthforum.mentalhealthforum_backend.dto.user;

import com.mentalhealthforum.mentalhealthforum_backend.dto.notification.NotificationPreferences;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ProfileVisibility;
import com.mentalhealthforum.mentalhealthforum_backend.enums.SupportRole;
import com.mentalhealthforum.mentalhealthforum_backend.service.OnboardingProfileData;
import com.mentalhealthforum.mentalhealthforum_backend.validation.ValidEmail;
import com.mentalhealthforum.mentalhealthforum_backend.validation.bio.ValidBio;
import com.mentalhealthforum.mentalhealthforum_backend.validation.displayName.ValidDisplayName;
import com.mentalhealthforum.mentalhealthforum_backend.validation.firstName.ValidFirstName;
import com.mentalhealthforum.mentalhealthforum_backend.validation.lastName.ValidLastName;
import com.mentalhealthforum.mentalhealthforum_backend.validation.timezone.ValidTimezone;
import com.mentalhealthforum.mentalhealthforum_backend.validation.url.ValidUrl;
import jakarta.validation.Valid;

public record UpdateUserProfileRequest(

        @ValidEmail
        String email,

        @ValidFirstName
        String firstName,

        @ValidLastName
        String lastName,

        @ValidBio
        String bio,

        @ValidDisplayName
        String displayName,

        @ValidUrl
        String avatarUrl,

        @ValidTimezone(nullable = true)
        String timezone,

        ProfileVisibility profileVisibility

        // SupportRole supportRole,

        //  @Valid
        //  NotificationPreferences notificationPreferences
) implements OnboardingProfileData {}
