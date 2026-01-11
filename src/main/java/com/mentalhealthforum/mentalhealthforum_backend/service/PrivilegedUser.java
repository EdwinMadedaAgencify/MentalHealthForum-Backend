package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding.OnboardingPolicy;
import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;
import com.mentalhealthforum.mentalhealthforum_backend.enums.InternalRole;
import com.mentalhealthforum.mentalhealthforum_backend.enums.RealmRole;
import com.mentalhealthforum.mentalhealthforum_backend.enums.SupportRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Interface defining privilege-checking behavior for users.
 * Implemented by both database entities (AppUser) and JWT-based contexts (ViewerContext).
 * Contains shared logic for role and group-based privilege checks.
 */
public interface PrivilegedUser {
    // Core data access methods - implementors must provide these
    Set<String> getRoles();
    Set<String> getGroups();

    // --- Helper Methods with Default Implementations ---
    public default boolean hasRole(RealmRole role){
        Set<String> roles = getRoles();
        return roles != null && roles.contains(role.getRoleName());
    }

    public default boolean isInGroup(GroupPath group){
        Set<String> groups = getGroups();
        if(groups == null || group == null) return false;
        return groups.stream().anyMatch(groupItem -> GroupPath.isInGroup(groupItem, group));
    }

    // Check if user has any of the specified roles
    public default boolean hasAnyRole(RealmRole... rolesToCheck){
        Set<String> roles = getRoles();
        if(roles == null || roles.isEmpty()) return false;
        for(RealmRole role: rolesToCheck){
            if(roles.contains(role.getRoleName())){
                return true;
            }
        }
        return false;
    }

    public default boolean isInAnyGroup(GroupPath... groupsToCheck){
        Set<String> groups = getGroups();
        if(groups == null || groups.isEmpty()) return false;
        for(GroupPath group: groupsToCheck){
            if(isInGroup(group)){
                return true;
            }
        }
        return false;
    }

    // --- Custom Computed Property Getters ---
    public default boolean isAdmin(){ return hasRole(RealmRole.ADMIN) || isInGroup(GroupPath.ADMINISTRATORS);}

    public default boolean isModerator(){
        return hasRole(RealmRole.MODERATOR) || isInGroup(GroupPath.MODERATORS);
    }

    public default boolean isPeerSupporter(){
        return hasRole(RealmRole.PEER_SUPPORTER) ||
                isInGroup(GroupPath.MEMBERS_TRUSTED) ||
                isInGroup(GroupPath.MODERATORS_PROFESSIONAL);
    }

    public default boolean isTrustedMember(){
        return hasRole(RealmRole.TRUSTED_MEMBER) ||
                isInGroup(GroupPath.MEMBERS_ACTIVE) ||
                isInGroup(GroupPath.MEMBERS_TRUSTED);
    }

    public default boolean isForumMember(){
        return hasRole(RealmRole.FORUM_MEMBER) ||
                (getGroups() != null && !getGroups().isEmpty());
    }

    public default boolean isPrivileged() {
        return isAdmin() || isModerator() || isPeerSupporter();
    }

    public default OnboardingPolicy.Result checkOnboardingPolicy(OnboardingProfileData onboardingProfileData) {
        List<OnboardingPolicy.Violation> violations = new ArrayList<>();
        String bio = onboardingProfileData.bio() != null? onboardingProfileData.bio().trim(): "";

        //  ROLE-SPECIFIC REQUIREMENTS (Mutually Exclusive Hierarchy)
        if (this.isPrivileged()) {
            if (bio.length() < 100) {
                violations.add(new OnboardingPolicy.Violation("bio", "Privileged roles require a bio of at least 100 characters for community trust."));
            }

            if (onboardingProfileData.timezone() == null || onboardingProfileData.timezone().isBlank()) {
                violations.add(new OnboardingPolicy.Violation("timezone", "Timezone is required for privileged users to coordinate support."));
            }

            if (onboardingProfileData.supportRole() == null || onboardingProfileData.supportRole() == SupportRole.NOT_SPECIFIED) {
                violations.add(new OnboardingPolicy.Violation("supportRole", "Please specify your support role."));
            }
        }
        else if (this.isTrustedMember()) {
            if (bio.length() < 50) {
                violations.add(new OnboardingPolicy.Violation("bio", "Trusted members require a bio of at least 50 characters."));
            }
        }
        else {
            if (bio.length() < 20) {
                violations.add(new OnboardingPolicy.Violation("bio", "Please provide a bio of at least 20 characters to join the community."));
            }
        }

        return violations.isEmpty() ? OnboardingPolicy.Result.success() : OnboardingPolicy.Result.failure(violations);
    }
}
