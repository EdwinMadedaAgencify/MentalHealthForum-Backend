package com.mentalhealthforum.mentalhealthforum_backend.dto;

import com.mentalhealthforum.mentalhealthforum_backend.enums.InternalRole;
import com.mentalhealthforum.mentalhealthforum_backend.service.PrivilegedUser;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUser;
import lombok.Getter;

import java.util.Set;

/**
 * Immutable DTO representing the authenticated viewer's context extracted from JWT.
 * Implements {@link PrivilegedUser} to provide the same privilege-checking API
 * as {@link AppUser} but without database dependency.
 *
 * <p>Used to pass viewer's identity and privileges to service methods
 * for context-aware operations (like privacy-aware user profile fetching).
 */
@Getter
public class ViewerContext implements PrivilegedUser {
    private final String userId;
    private final String email;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String fullName;
    private final Set<String> roles;
    private final Set<String> groups;

    // Custom constructor to ensure non-null collections
    public ViewerContext(
            String userId,
            String email,
            String username,
            String firstName,
            String lastName,
            String fullName,
            Set<String> roles,
            Set<String> groups
    ) {
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.fullName = fullName;
        this.roles = roles != null ? Set.copyOf(roles) : Set.of(); // Defensive copy
        this.groups = groups != null ? Set.copyOf(groups) : Set.of(); // Defensive copy
    }

    public boolean hasInternalRole(InternalRole role){
        Set<String> roles = getRoles();
        return roles != null && roles.contains(role.getRoleName());
    }

    public boolean isOnboarding(){
        return hasInternalRole(InternalRole.ONBOARDING);
    }
}
