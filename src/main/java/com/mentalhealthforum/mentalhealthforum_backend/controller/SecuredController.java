package com.mentalhealthforum.mentalhealthforum_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * A simple controller to verify the security configuration and JWT processing.
 *
 * This endpoint requires authentication via either the ACCESS_TOKEN cookie or
 * the Authorization: Bearer header.
 */
@RestController
@RequestMapping("/api/secure")
@PreAuthorize("!hasRole('ONBOARDING')")
public class SecuredController {

    /**`
     * Endpoint to test basic authentication and retrieve JWT claims.
     * @param jwt The authenticated JWT object provided by Spring Security
     * @return A Mono containing user details derived from the JWT claims.
     */
    @GetMapping("/user-info")
    public Mono<Map<String, Object>> getUserInfo(@AuthenticationPrincipal Jwt jwt) {
        // The 'sub' (subject) claim is typically the Keycloak user ID
        String userId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");

        // The custom roles are usually available under the 'roles' claim
        List<String> roles = jwt.getClaimAsStringList("realm_access");
        if (roles == null) {
            roles = List.of();
        }

        return Mono.just(Map.of(
                "message", "Access granted via Cookie or Bearer Header!",
                "email", userId,
                "username", username,
                "roles", roles
        ));
    }

    /**
     * Endpoint demonstrating role-based access control (RBAC).
     * Only users with the 'ADMIN' role can access this.
     */
    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<String> getAdminContent() {
        return Mono.just("Welcome, Administrator. Your security setup works!");
    }
}