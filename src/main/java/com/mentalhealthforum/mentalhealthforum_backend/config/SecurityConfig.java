package com.mentalhealthforum.mentalhealthforum_backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
// Change to the reactive security annotation
@EnableReactiveMethodSecurity
public class SecurityConfig {

    private final String principalClaimName;

    // Assuming SecurityExceptionHandler implements ServerAuthenticationEntryPoint/ServerAccessDeniedHandler
    @Autowired
    private SecurityExceptionHandler securityExceptionHandler;

    public SecurityConfig(
            JwtProperties jwtProperties
    ){
        this.principalClaimName = jwtProperties.getPrincipalClaimName();
    }

    private static final String[] AUTH_WHITELIST = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/**",
            "/error/**",
            // Consolidated authentication paths for login, refresh, and logout
            "/api/v1/auth/**"
    };

    /**
     * Defines the reactive security filter chain.
     * @param http The reactive HTTP security configuration object.
     * @return The configured SecurityWebFilterChain.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(auth -> auth
                        .pathMatchers(AUTH_WHITELIST).permitAll() // Allow unauthenticated access to whitelist
                        .anyExchange().authenticated() // Require authentication for all other requests
                )
                // Use the combined handler for both 401 and 403 (Assuming SecurityExceptionHandler is reactive)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                // Configure OAuth2 Resource Server to handle JWT validation
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
                        // Apply exception handlers specifically for Resource Server authentication issues
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .build();
    }

    @Bean
    public JwtAuthConverter jwtAuthConverter() {
        return new JwtAuthConverter(principalClaimName);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(){
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}