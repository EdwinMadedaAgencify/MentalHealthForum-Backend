package com.mentalhealthforum.mentalhealthforum_backend.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;


@Configuration
// Change to the reactive security annotation
@EnableScheduling // This turns on the background task engine
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    private final String principalClaimName;
    private final SecurityExceptionHandler securityExceptionHandler;  // SecurityExceptionHandler implements ServerAuthenticationEntryPoint/ServerAccessDeniedHandler
    private final OnboardingAuthorizationManager onboardingAuthorizationManager;

    public SecurityConfig(
            JwtProperties jwtProperties,
            SecurityExceptionHandler securityExceptionHandler,
            CookieToJwtConverter cookieToJwtConverter,
            OnboardingAuthorizationManager onboardingAuthorizationManager
    ){
        this.principalClaimName = jwtProperties.getPrincipalClaimName();
        this.securityExceptionHandler = securityExceptionHandler;
        this.onboardingAuthorizationManager = onboardingAuthorizationManager;
    }

    private static final String[] AUTH_WHITELIST = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/**",
            "/error/**",
            "/api/users/register/**",
            "/api/auth/**" // Consolidated authentication paths for login, refresh, and logout
    };

    /**
     * Creates a composite converter that checks the cookie OR the Authorization header.
     */
    private ServerAuthenticationConverter compositeTokenConverter(){
        // 1. Standard Converter: Checks for Authorization: Bearer <token> header
        ServerAuthenticationConverter bearerConverter = new ServerBearerTokenAuthenticationConverter();

        // 2. Custom Converter: Checks for ACCESS_TOKEN cookie
        ServerAuthenticationConverter cookieConverter = new CookieToJwtConverter();

        return (exchange -> Mono.just(exchange)
                .flatMap(cookieConverter::convert)
                .switchIfEmpty(Mono.defer(() -> bearerConverter.convert(exchange)))
        );
    }

    /**
     * Defines the reactive security filter chain.
     * @param http The reactive HTTP security configuration object.
     * @return The configured SecurityWebFilterChain.
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // CRITICAL FOR COOKIE AUTH: CSRF must be disabled as it's typically stateful
                // and cookies are stateless here.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(auth -> auth
                        .pathMatchers(AUTH_WHITELIST).permitAll() // Allow unauthenticated access to whitelist
//                        .anyExchange().authenticated() // Require authentication for all other requests
                                .anyExchange().access(onboardingAuthorizationManager)
                )
                // Use the combined handler for both 401 and 403
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                // Configure OAuth2 Resource Server to handle JWT validation
                .oauth2ResourceServer(oauth2 -> oauth2
                        // **FIX:** We set the custom composite converter here
                        .bearerTokenConverter(compositeTokenConverter())  // <-- Now checks cookie OR header
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
        // Allow the frontend domain
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:3001"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // CRITICAL FOR COOKIE AUTH: Must allow credentials (cookies) to be sent cross-origin
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
}