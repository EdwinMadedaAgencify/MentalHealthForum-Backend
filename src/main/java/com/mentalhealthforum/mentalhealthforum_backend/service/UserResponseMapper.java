package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.UserResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ProfileVisibility;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUser;
import org.springframework.stereotype.Component;

/**
 * Maps {@link AppUser} entities to {@link UserResponse} DTOs with context-aware
 * privacy rules. Differentiates between:
 * <ul>
 *   <li>Self-view: All fields exposed</li>
 *   <li>Others-view: Privacy rules based on target's profileVisibility and viewer's privileges</li>
 * </ul>
 *
 * <p>Privacy rules:
 * <ul>
 *   <li>Admins/Moderators cannot be anonymous (transparency)</li>
 *   <li>SupportRole only visible to privileged viewers (admin/moderator/peer supporter)</li>
 *   <li>Sensitive fields (email, username, notification preferences) never exposed to others</li>
 * </ul>
 *
 * <p>Business rules (configurable via constants):
 * <ul>
 *   <li>ENFORCE_ADMIN_TRANSPARENCY: Admins/Moderators cannot be anonymous</li>
 *   <li>SHOW_SUPPORTROLE_TO_PRIVILEGED_ONLY: SupportRole only visible to privileged viewers</li>
 * </ul>
 *
 */
@Component
public class UserResponseMapper {

    // Business rule flags (could be made configurable if needed)
    private static final boolean ENFORCE_ADMIN_TRANSPARENCY = true;
    private static final boolean SHOW_SUPPORTROLE_TO_PRIVILEGED_ONLY = true;

    /**
     * Creates a minimal public view of a user with only always-public fields.
     * Used for unauthenticated access or as a base for other views.
     *
     * @param targetUser The user entity to map
     * @return UserResponse with only public fields (no sensitive data)
     */
    public UserResponse toPublicUserResponse(AppUser targetUser){
        // Map ONLY always-public fields
        UserResponse userResponse = new UserResponse(
                targetUser.getKeycloakId(),  // Using keycloakId as email in response
                targetUser.getDateJoined(),
               false,
                targetUser.getDisplayName(),
                targetUser.getPostsCount(),
                targetUser.getReputationScore(),
                targetUser.getLastActiveAt(),
                targetUser.getLastPostedAt(),
                targetUser.getIsActive(),
                targetUser.getRoles(),
                targetUser.getGroups()
        );

        userResponse.setInitials(targetUser.getInitials());
        return userResponse;
    }

    /**
     * Creates a full self-view of a user with all fields exposed.
     * Includes private/sensitive fields that only the user themselves should see.
     *
     * @param targetUser The user entity to map (viewing themselves)
     * @return Complete UserResponse with all fields, including sensitive data
     */
    public UserResponse toSelfResponse(AppUser targetUser){
        UserResponse response = toPublicUserResponse(targetUser);
        response.setSelf(true);

        // Add all private fields for self-view
        response.setUsername(targetUser.getUsername());
        response.setEmail(targetUser.getEmail());
        response.setFirstName(targetUser.getFirstName());
        response.setLastName( targetUser.getLastName());
        response.setAvatarUrl(targetUser.getAvatarUrl());
        response.setBio(targetUser.getBio());
        response.setTimezone(targetUser.getTimezone());
        response.setLanguage(targetUser.getLanguage());

        response.setPendingEmail(targetUser.getPendingEmail());

        // Override profile visibility for admins/moderators - they cannot be anonymous
        // This ensures transparency even if they set PRIVATE in database
        response.setProfileVisibility(
                targetUser.isAdmin() || targetUser.isModerator()
                        ? ProfileVisibility.MEMBERS_ONLY
                        : targetUser.getProfileVisibility());

        response.setSupportRole(targetUser.getSupportRole());
        response.setNotificationPreferences(targetUser.getNotificationPreferences());


        return response;
    }

    /**
     * Creates a privacy-aware view of a user for other viewers.
     * Applies rules based on target user's profile visibility and viewer's privileges.
     *
     * @param targetUser The user entity being viewed
     * @param viewerContext The viewer's context (privileges, relationship to target)
     * @return Privacy-filtered UserResponse appropriate for the viewer
     */
    public UserResponse toOthersResponse(AppUser targetUser, ViewerContext viewerContext){
        // START with PUBLIC fields only
        UserResponse response =  toPublicUserResponse(targetUser);
        response.setSelf(false);

        // Always clear sensitive fields
        response.setUsername(null);
        response.setEmail(null);
        response.setPendingEmail(null);
        response.setNotificationPreferences(null);

        // Check viewer privileges
        boolean isViewerPrivileged = isViewerPrivileged(viewerContext);

        // --- ADMIN/MODERATOR TRANSPARENCY RULE (APPLIES ALWAYS) ---
        if(ENFORCE_ADMIN_TRANSPARENCY && (targetUser.isAdmin() || targetUser.isModerator() || viewerContext.isAdmin() || viewerContext.isModerator())){
           return buildAdminModeratorResponse(response, targetUser, isViewerPrivileged);
        }

        // --- REGULAR USERS: Apply profile visibility rules ---
       return buildRegularUserResponse(response, targetUser, isViewerPrivileged);
    }

    /**
     * Main entry point for context-aware user mapping.
     * Determines appropriate view (self/others/public) based on viewer context.
     *
     * @param targetUser The user entity to map
     * @param viewerContext The viewer's context, null for unauthenticated access
     * @return Appropriately filtered UserResponse based on context
     */
    public UserResponse mapUserBasedOnContext(AppUser targetUser, ViewerContext viewerContext) {
        if(viewerContext == null){
            // Unauthenticated access - return minimal public info
            return toPublicUserResponse(targetUser);
        }
        boolean isSelf = targetUser.getKeycloakId().toString().equals(viewerContext.getUserId());
        return isSelf ? toSelfResponse(targetUser) : toOthersResponse(targetUser, viewerContext);
    }

    // -- Helper Methods --

    /**
     * Determines if the viewer has special privileges (admin/moderator/peer supporter).
     * Privileged viewers can see SupportRole and have extended access to private profiles.
     */
    private boolean isViewerPrivileged(ViewerContext viewerContext){
        return viewerContext != null &&
                (viewerContext.isAdmin() ||
                        viewerContext.isModerator() ||
                        viewerContext.isPeerSupporter());
    };

    /**
     * Builds response for admin/moderator users with transparency rules applied.
     * Admins/moderators are always identifiable regardless of their profile setting.
     */
    private UserResponse buildAdminModeratorResponse(UserResponse baseResponse, AppUser targetUser, boolean isViewerPrivileged){

        // Core identity - always visible
        baseResponse.setDisplayName(targetUser.getDisplayName());
        baseResponse.setFirstName(targetUser.getFirstName());
        baseResponse.setLastName(targetUser.getLastName());
        baseResponse.setAvatarUrl(targetUser.getAvatarUrl());

        // Basic profile info - always visible for transparency
        baseResponse.setBio(targetUser.getBio());

        // Preferences/details - visible to all authenticated members
        baseResponse.setTimezone(targetUser.getTimezone());
        baseResponse.setLanguage(targetUser.getLanguage());

        // Profile visibility override: admins/moderators can't be fully private
        baseResponse.setProfileVisibility(
                targetUser.isAdmin() || targetUser.isModerator()
                        ? ProfileVisibility.MEMBERS_ONLY
                        : targetUser.getProfileVisibility());

        // SupportRole only for privileged viewers
        if (SHOW_SUPPORTROLE_TO_PRIVILEGED_ONLY && isViewerPrivileged) {
            baseResponse.setSupportRole(targetUser.getSupportRole());
        }

        return baseResponse; // Early return - admin/moderator logic handled
    }
    /**
     * Builds response for regular users based on their profile visibility setting.
     */
    private UserResponse buildRegularUserResponse(UserResponse baseResponse, AppUser targetUser, boolean isViewerPrivileged){
        switch (targetUser.getProfileVisibility()){
            case MEMBERS_ONLY:
               return buildMembersOnlyResponse(baseResponse, targetUser, isViewerPrivileged);
            case PRIVATE:
            default:
               return buildPrivateResponse(baseResponse, targetUser, isViewerPrivileged);
        }
    }
    /**
     * Builds response for users with MEMBERS_ONLY profile visibility.
     * Shows profile details to all authenticated members.
     */
    private UserResponse buildMembersOnlyResponse(UserResponse baseResponse, AppUser targetUser, boolean isViewerPrivileged){
        // Show profile details to members
        baseResponse.setFirstName(targetUser.getFirstName());
        baseResponse.setLastName( targetUser.getLastName());
        baseResponse.setAvatarUrl(targetUser.getAvatarUrl());
        baseResponse.setBio(targetUser.getBio());
        baseResponse.setTimezone(targetUser.getTimezone());
        baseResponse.setLanguage(targetUser.getLanguage());
        baseResponse.setProfileVisibility(ProfileVisibility.MEMBERS_ONLY);

        // SupportRole: Only show to privileged viewers
        if (SHOW_SUPPORTROLE_TO_PRIVILEGED_ONLY && isViewerPrivileged) {
            baseResponse.setSupportRole(targetUser.getSupportRole());
        }
        return baseResponse;
    }
    /**
     * Builds response for users with PRIVATE profile visibility.
     * Shows minimal information to protect user privacy.
     */
    private UserResponse buildPrivateResponse(UserResponse baseResponse, AppUser targetUser, boolean isViewerPrivileged){
        // Regular users with private profile - minimal info
        baseResponse.setFirstName(null);
        baseResponse.setLastName(null);
        baseResponse.setAvatarUrl(null);
        baseResponse.setBio(null);
        baseResponse.setTimezone(null);
        baseResponse.setLanguage(null);
        baseResponse.setSupportRole(null);
        baseResponse.setProfileVisibility(ProfileVisibility.PRIVATE);

        return baseResponse;
    }

}
