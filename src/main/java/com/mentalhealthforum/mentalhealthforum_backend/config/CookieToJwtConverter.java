package com.mentalhealthforum.mentalhealthforum_backend.config;

import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Custom ServerAuthenticationConverter to extract the JWT from the HTTP-only cookie.
 * This is necessary because Spring Security's OAuth2 Resource Server defaults to checking
 * the Authorization: Bearer header, but our frontend uses secure, HTTP-only cookies.
 */
@Component
public class CookieToJwtConverter implements ServerAuthenticationConverter {

    private static final String ACCESS_TOKEN_NAME = "ACCESS_TOKEN";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        // 1. Try to find the ACCESS_TOKEN cookie
        HttpCookie jwtCookie = request.getCookies().getFirst(ACCESS_TOKEN_NAME);

        if(jwtCookie == null || jwtCookie.getValue().isBlank()){
            // If the cookie is not present, return empty, allowing subsequent converters
            // (like BearerTokenServerAuthenticationConverter, if chained) or the chain to fail.
            return Mono.empty();
        }

        String tokenValue = jwtCookie.getValue();

        // 2. Wrap the token value in a format Spring Security understands
        // We use the Bearer token type here to trick the underlying JwtResourceServer
        // parser into handling it as if it came from an Authorization header.
        return Mono.just(tokenValue)
                .map(BearerTokenAuthenticationToken::new);
    }
}
