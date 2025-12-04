package com.mentalhealthforum.mentalhealthforum_backend.mapper;

import com.mentalhealthforum.mentalhealthforum_backend.dto.UserResponse;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUser;
import org.springframework.stereotype.Component;

@Component
public class UserResponseMapper {

    public UserResponse toPublicUserResponse(AppUser appUser){
        // Map ONLY always-public fields
        return new UserResponse(
                appUser.getKeycloakId(),  // Using keycloakId as userId in response
                appUser.getDateJoined(),
               false,
                appUser.getDisplayName(),
                appUser.getPostsCount(),
                appUser.getReputationScore(),
                appUser.getLastActiveAt(),
                appUser.getLastPostedAt(),
                appUser.getIsActive(),
                appUser.getRoles(),
                appUser.getGroups()
        );
    }

    public UserResponse toSelfResponse(AppUser appUser){
        UserResponse response = toPublicUserResponse(appUser);
        response.setSelf(true);

        // Add all private fields for self-view
        response.setUsername(appUser.getUsername());
        response.setEmail(appUser.getEmail());
        response.setFirstName(appUser.getFirstName());
        response.setLastName( appUser.getLastName());
        response.setAvatarUrl(appUser.getAvatarUrl());
        response.setBio(appUser.getBio());
        response.setTimezone(appUser.getTimezone());
        response.setLanguage(appUser.getLanguage());
        response.setProfileVisibility(appUser.getProfileVisibility());
        response.setSupportRole(appUser.getSupportRole());
        response.setNotificationPreferences(appUser.getNotificationPreferences());

        return response;
    }

    public UserResponse toOthersResponse(AppUser appUser){
        // START with PUBLIC fields only
        UserResponse response =  toPublicUserResponse(appUser);
        response.setSelf(false);

        // Always clear sensitive fields
        response.setUsername(null);
        response.setEmail(null);
        response.setNotificationPreferences(null);

        // Apply visibility rules
        switch (appUser.getProfileVisibility()){
            case MEMBERS_ONLY:
                // Show profile details to members
                response.setFirstName(appUser.getFirstName());
                response.setLastName( appUser.getLastName());
                response.setAvatarUrl(appUser.getAvatarUrl());
                response.setBio(appUser.getBio());
                response.setTimezone(appUser.getTimezone());
                response.setLanguage(appUser.getLanguage());
                response.setSupportRole(appUser.getSupportRole());
                break;

            case PRIVATE:
            default:
                // Private profile - only display name (already set)
                // Clear any personal info
                response.setFirstName(null);
                response.setLastName(null);
                response.setAvatarUrl(null);
                response.setBio(null);
                response.setTimezone(null);
                response.setLanguage(null);
                response.setSupportRole(null);
                break;
        }

        return response;
    }

    public UserResponse mapUserBasedOnContext(AppUser appUser, String currentUserId) {
        boolean isSelf = appUser.getKeycloakId().toString().equals(currentUserId);
        return isSelf ? toSelfResponse(appUser) : toOthersResponse(appUser);
    }
}
