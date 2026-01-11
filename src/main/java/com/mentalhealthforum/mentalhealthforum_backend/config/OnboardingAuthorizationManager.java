package com.mentalhealthforum.mentalhealthforum_backend.config;


import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class OnboardingAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    @Override
    public Mono<AuthorizationDecision> check(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuthenticationToken -> {
                    boolean isOnboarding = jwtAuthenticationToken.getAuthorities().stream()
                            .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ONBOARDING"));

                    // If not onboarding Let subsequent checks (Method Security) handle it.
                    if(!isOnboarding){
                        return new AuthorizationDecision(true);
                    }

                    // If onboarding, check the path
                    String path = context.getExchange().getRequest().getPath().value();

                    // Allow them to update their own profile to satisfy requirements
                    if(path.startsWith("/api/users") || path.startsWith("/api/auth")){
                        return new AuthorizationDecision(true);
                    }

                    // Block everything else
                    return new AuthorizationDecision(false);
                })
                .defaultIfEmpty(new AuthorizationDecision(false));
    }
}
