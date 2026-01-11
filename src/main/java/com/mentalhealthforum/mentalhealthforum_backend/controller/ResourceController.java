package com.mentalhealthforum.mentalhealthforum_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller providing role-based access control (RBAC) endpoints, converted to use
 * the Spring WebFlux reactive standard (Mono return types and Jwt principal).
 */
@RestController
@RequestMapping("/api/resources")
@PreAuthorize("!hasRole('ONBOARDING')")
public class ResourceController {

    /**
     * Helper method to construct the response message using the Jwt claims.
     * We use the 'roles' claim which is typically where Keycloak puts custom roles.
     * @param accessLevel Descriptive message for the access level.
     * @param jwt The authenticated JWT object.
     * @return The formatted response string.
     */
    private String createResponseMessage(String accessLevel, Jwt jwt){
        // Get username from standard Keycloak claim
        String username = jwt.getClaimAsString("preferred_username");

        // Get roles/authorities from the custom 'roles' claim (adjust claim name if needed)
        List<String> rolesList = jwt.getClaimAsStringList("roles");
        String roles = (rolesList != null) ? String.join(" ", rolesList) : "N/A";

        return String.format(
                """
                %s
                Accessed by: %s
                Authorities (from JWT claims): %s
                """, accessLevel, username, roles
        ).trim();
    }

    @PreAuthorize("hasRole('FORUM_MEMBER')")
    @GetMapping("/member")
    @ResponseStatus(HttpStatus.OK)
    public Mono<String> memberResource(@AuthenticationPrincipal Jwt jwt){
        String message = createResponseMessage("Accessible to forum members.", jwt);
        return Mono.just(message);
    }

    @PreAuthorize("hasRole('MODERATOR')")
    @GetMapping("/moderator")
    @ResponseStatus(HttpStatus.OK)
    public Mono<String> moderatorResource(@AuthenticationPrincipal Jwt jwt){
        String message = createResponseMessage("Accessible to forum moderators.", jwt);
        return Mono.just(message);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    @ResponseStatus(HttpStatus.OK)
    public Mono<String> adminResource(@AuthenticationPrincipal Jwt jwt){
        String message = createResponseMessage("Accessible to forum Admins.", jwt);
        return Mono.just(message);
    }

    @PreAuthorize("hasAnyRole('FORUM_MEMBER', 'TRUSTED_MEMBER', 'PEER_SUPPORTER')")
    @GetMapping("/forum/topics")
    @ResponseStatus(HttpStatus.OK)
    public Mono<String> forumTopics(@AuthenticationPrincipal Jwt jwt){
        String message = createResponseMessage("Accessible to all members (forum, trusted, peer supporter).", jwt);
        return Mono.just(message);
    }
}