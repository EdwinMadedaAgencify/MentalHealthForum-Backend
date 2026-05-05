package com.mentalhealthforum.mentalhealthforum_backend.config;


import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
public class OnboardingAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuthenticationToken -> {
                    boolean isOnboarding = hasRole(jwtAuthenticationToken, "ROLE_ONBOARDING");
                    String path = context.getExchange().getRequest().getPath().value();

                    // If onboarding, check the path
                    if(isOnboarding){
                        // Allow them to update their own profile to satisfy requirements
                        if(path.startsWith("/api/users") ||
                            path.startsWith("/api/auth") ||
                            path.startsWith("/api/onboarding")){
                            return new AuthorizationDecision(true);
                        }

                        // Block everything else
                        return new AuthorizationDecision(false);
                    }

                    // For admin paths, check ADMIN role
                    if(path.startsWith("/api/admin")){
                        boolean isAdmin = hasRole(jwtAuthenticationToken, "ROLE_ADMIN");
                        return new AuthorizationDecision(isAdmin);
                    }

                    if(path.startsWith("/api/moderator")){
                        boolean isModeratorOrAdmin = hasAnyRole(jwtAuthenticationToken,  "ROLE_ADMIN", "ROLE_MODERATOR");
                        return new AuthorizationDecision(isModeratorOrAdmin);
                    }

                    if(path.startsWith("/api/peer")){
                        boolean isPeerOrHigher = hasAnyRole(jwtAuthenticationToken, "ROLE_ADMIN", "ROLE_MODERATOR", "ROLE_PEER_SUPPORTER");
                        return new AuthorizationDecision(isPeerOrHigher);
                    }

                    // If not onboarding Let subsequent checks (Method Security) handle it.
                    // For all other paths, allow
                    return new AuthorizationDecision(true);

                })
                .defaultIfEmpty(new AuthorizationDecision(false));
    }

    // Helper methods
    private boolean hasRole(JwtAuthenticationToken jwtAuthenticationToken, String role){
        return jwtAuthenticationToken.getAuthorities().stream()
                .anyMatch(grantedAuthority ->
                        grantedAuthority.getAuthority().equals(role));
    }

    private boolean hasAnyRole(JwtAuthenticationToken jwtAuthenticationToken, String ...roles){
        Set<String> roleSet = Set.of(roles);
        return jwtAuthenticationToken.getAuthorities().stream()
                .anyMatch(grantedAuthority ->
                        roleSet.contains( grantedAuthority.getAuthority()));
    }


}
