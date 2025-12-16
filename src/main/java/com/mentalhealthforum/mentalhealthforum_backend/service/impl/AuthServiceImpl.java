package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentalhealthforum.mentalhealthforum_backend.config.KeycloakProperties;
import com.mentalhealthforum.mentalhealthforum_backend.dto.JwtResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.LoginRequest;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.AuthenticationFailedException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.UserActionRequiredException;
import com.mentalhealthforum.mentalhealthforum_backend.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Implementation of the AuthService using WebClient to interact with Keycloak
 * for manual authentication (ROPC Grant) and token refreshing.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    // Constant for the specific Keycloak error that indicates required actions are pending.
    private static final String KEYCLOAK_REQUIRED_ACTION_ERROR = "Account is not fully set up";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;
    private final String logoutUri;

    public AuthServiceImpl(
            WebClient.Builder webClientBuilder,
            KeycloakProperties properties) {

        String authServerUrl = properties.getAuthServerUrl();
        String realm = properties.getRealm();

        this.clientId = properties.getResource();
        this.clientSecret = properties.getCredentials().getSecret();

        // Base URI for token and refresh endpoints
        String tokenUri = String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm);

        // URI for the logout/revocation endpoint
        this.logoutUri = String.format("%s/realms/%s/protocol/openid-connect/logout", authServerUrl, realm);

        // WebClient MUST be built with the specific token URI
        this.webClient = webClientBuilder.baseUrl(tokenUri).build();
    }

    /**
     * Authenticates a user against Keycloak using ROPC (Resource Owner Password Credentials) Grant.
     * @param request LoginRequest containing username and password.
     * @return Mono<JwtResponse> containing the access and refresh tokens.
     */
    @Override
    public Mono<JwtResponse> authenticate(LoginRequest request){
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();

        // 1. OAuth2 Grant Type and Client Credentials
        formData.add("grant_type", "password");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);

        // 2. AppUser Credentials
        formData.add("username", request.username());
        formData.add("password", request.password());

        // 3. Adding 'openid' scope for userinfo endpoint access
        formData.add("scope", "openid");

        return webClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(
                        // Intercept only 4xx errors (Invalid Credentials, etc.)
                        HttpStatusCode::is4xxClientError,
                        clientResponse -> {
                            return clientResponse.bodyToMono(String.class)
                                    .doOnNext(body ->  log.error("Keycloak Login Error Response (4xx): {}", body))
                                    .flatMap(body -> {
                                        try {

                                            Map<String, String> errorBody = objectMapper.readValue(body, new TypeReference<Map<String, String>>() {});
                                            String errorDescription = errorBody.getOrDefault("error_description", "Authentication failed.");

                                            // Pass error description directly with minimal hints
                                            if(KEYCLOAK_REQUIRED_ACTION_ERROR.equals(errorDescription)){
                                                return Mono.error(new UserActionRequiredException(
                                                        errorDescription + ". Please check your inbox for verification links or complete required profile updates."
                                                ));
                                            }

                                            return Mono.error(new AuthenticationFailedException(errorDescription));

                                        } catch (JsonProcessingException e) {
                                            log.error("Could not parse Keycloak error response as JSON: {}", body, e);
                                            return Mono.error(new AuthenticationFailedException(
                                                    "Authentication failed."
                                            ));
                                        }
                                    });
                        })
                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        clientResponse -> {
                            return clientResponse.bodyToMono(String.class)
                                    .doOnNext(body -> log.error("Keycloak Server Error (5xx): {}", body))
                                    .then(Mono.error(new ApiException(
                                            "Authentication service is temporarily unavailable.",
                                            ErrorCode.AUTHENTICATION_SERVICE_ERROR
                                    )));
                        })
                .bodyToMono(JwtResponse.class)
                // ADD THIS: Handle connection/timeout errors
                .onErrorResume(WebClientRequestException.class, e -> {
                    log.error("Cannot connect to authentication service: {}", e.getMessage());
                    return Mono.error(new ApiException(
                            "Authentication service is temporarily unavailable. Please try again later.",
                            ErrorCode.AUTHENTICATION_SERVICE_ERROR,
                            e
                    ));
                })
                .onErrorResume(io.netty.channel.ConnectTimeoutException.class, e -> {
                    log.error("Connection timeout to authentication service: {}", e.getMessage());
                    return Mono.error(new ApiException(
                            "Authentication service connection timeout. Please try again.",
                            ErrorCode.AUTHENTICATION_SERVICE_ERROR,
                            e
                    ));
                });
    }

    /**
     * Exchanges a refresh token for a new set of access and refresh tokens using the Refresh Token Grant.
     * @param refreshToken The expired refresh token.
     * @return Mono<JwtResponse> containing the new tokens.
     */
    @Override
    public Mono<JwtResponse> refreshTokens(String refreshToken){
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();

        // 1. Correct Grant Type and Client Credentials
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);

        // 2. The token to be refreshed
        formData.add("refresh_token", refreshToken);

        return webClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(
                        // Intercept only 4xx errors (Invalid Token)
                        HttpStatusCode::is4xxClientError,
                        response ->  {
                            return response.bodyToMono(String.class)
                                    .doOnNext(body -> log.error("Keycloak Refresh Failed (4xx) : {}", body))
                                    .flatMap(body -> {
                                        return Mono.error(new AuthenticationFailedException(
                                                "Token refresh failed. The refresh token is invalid or expired."
                                        ));
                                    });
                        })
                .bodyToMono(JwtResponse.class);
    }

    @Override
    public Mono<Void> logout(String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();

        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("refresh_token", refreshToken);

        // Build a new WebClient to target the absolute logout URI
        return WebClient.builder().build().post()
                .uri(logoutUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        response -> {
                            return response.bodyToMono(String.class)
                                    .doOnNext(body -> log.warn("Keycloak Logout Failed ({}): {}", response.statusCode(), body))
                                    .flatMap(body -> {
                                        return Mono.error(new ApiException(
                                                "Logout failed. Please try again.",
                                                ErrorCode.AUTHENTICATION_SERVICE_ERROR,
                                                null
                                        ));
                                    });
                        })
                .bodyToMono(Void.class)
                .doOnError(e -> log.error("Unexpected error during Keycloak logout: {}", e.getMessage()));
    }
}