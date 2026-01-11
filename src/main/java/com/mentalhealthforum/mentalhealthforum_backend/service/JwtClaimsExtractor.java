package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracts user identity and privilege information from JWT claims.
 * Transforms JWT data into a {@link ViewerContext} DTO that implements
 * {@link PrivilegedUser} for consistent privilege checking.
 *
 * <p>Extracts:
 * <ul>
 *   <li>Basic identity: email, email, username, names, lastName</li>
 *   <li>Privileges: roles from 'realm_access.roles' claim</li>
 *   <li>Groups: from 'groups' claim</li>
 * </ul>
 */
@Component
public class JwtClaimsExtractor {
    private static final String EMAIL = "email";
    private static final String PREFERRED_USERNAME = "preferred_username";
    private static final String GIVEN_NAME = "given_name";
    private static final String FAMILY_NAME = "family_name";
    private static final String NAME = "name";
    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String GROUPS = "groups";

    public ViewerContext extractViewerContext(Jwt jwt){
        String userId = jwt.getSubject();
        String email = jwt.getClaimAsString(EMAIL);
        String username = jwt.getClaimAsString(PREFERRED_USERNAME);
        String firstName = jwt.getClaimAsString(GIVEN_NAME);
        String lastName = jwt.getClaimAsString(FAMILY_NAME);
        String fullName = jwt.getClaimAsString(NAME);
        Set<String> roles = extractRealmRoles(jwt);
        Set<String> groups = extractGroups(jwt);

        return new ViewerContext(userId, email, username, firstName, lastName, fullName, roles, groups);
    }

    /**
     * Extracts only realm-level roles (ignores client-specific roles).
     * Realm roles are used for application-level privileges (admin, moderator, etc.).
     */
    private Set<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS);
        if(realmAccess != null){
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get(ROLES);
            return roles != null?
                    Set.copyOf(new HashSet<>(roles)) // Double protection
                    : Set.of();
        }
        return Set.of();
    }

    /**
     * Extracts group memberships from JWT.
     * Groups are used for forum-specific privileges and visibility.
     */
    private Set<String> extractGroups(Jwt jwt) {
        List<String> groups = jwt.getClaimAsStringList(GROUPS);
        return groups != null
                ? Set.copyOf(new HashSet<>(groups))
                : Set.of();
    }
}
