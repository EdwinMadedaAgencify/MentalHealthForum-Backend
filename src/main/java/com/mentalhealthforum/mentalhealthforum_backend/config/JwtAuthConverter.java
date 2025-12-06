package com.mentalhealthforum.mentalhealthforum_backend.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Custom REACTIVE converter to map Keycloak realm roles to Spring authorities.
 * It must return a Mono<AbstractAuthenticationToken> for WebFlux compatibility.
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    private final String principalClaimName;

    public JwtAuthConverter(String principalClaimName) {
        this.principalClaimName = principalClaimName;
    }

    /**
     * Converts a blocking Jwt object into a reactive Mono containing the authentication token.
     * The roles extraction is a synchronous operation, so it's wrapped in a Mono.
     */
    @Override
    public Mono<AbstractAuthenticationToken> convert(@NotNull Jwt jwt) {
        return Mono.just(extractAuthenticationToken(jwt));
    }

    private AbstractAuthenticationToken extractAuthenticationToken(Jwt jwt) {
        Collection<? extends GrantedAuthority> authorities = extractRealmRoles(jwt);
        String principal = jwt.getClaimAsString(principalClaimName);
        return new JwtAuthenticationToken(jwt, authorities, principal);
    }

    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(REALM_ACCESS);
        if (realmAccess == null || !realmAccess.containsKey(ROLES)) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get(ROLES);

        return roles.stream()
                .filter(Objects::nonNull)
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
                .collect(Collectors.toSet());
    }
}