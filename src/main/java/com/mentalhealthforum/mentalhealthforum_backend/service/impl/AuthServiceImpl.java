package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.config.KeycloakProperties;
import com.mentalhealthforum.mentalhealthforum_backend.dto.JwtResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.LoginRequest;
import com.mentalhealthforum.mentalhealthforum_backend.service.AuthService;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Implementation of the AuthService using WebClient to interact with Keycloak
 * for manual authentication (ROPC Grant) and token refreshing.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;

    public AuthServiceImpl(
            WebClient.Builder webClientBuilder,
            KeycloakProperties properties) {

        String authServerUrl = properties.getAuthServerUrl();
        String realm = properties.getRealm();

        // FINAL CORRECTION: Using the exact properties provided by the user
        this.clientId = properties.getResource(); // Now maps to 'keycloak.resource'
        this.clientSecret = properties.getCredentials().getSecret(); // Now maps to 'keycloak.credentials.secret'

        String tokenUri = String.format("%s/realms/%s/protocol/openid-connect/token", authServerUrl, realm);

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

        // 2. User Credentials
        formData.add("username", request.username());
        formData.add("password", request.password());

        return webClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        response -> {
                            return response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        System.err.println("Keycloak Error Response: " + body);
                                        return Mono.error(new WebClientResponseException(
                                                response.statusCode().value(),
                                                "Keycloak Authentication Failed",
                                                response.headers().asHttpHeaders(),
                                                body.getBytes(),
                                                null
                                        ));
                                    });
                        })
                .bodyToMono(JwtResponse.class);
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
                        HttpStatusCode::is4xxClientError,
                        response ->  {
                            return response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        System.err.println("Keycloak Refresh Failed: " + body);
                                        return Mono.error(new WebClientResponseException(
                                                response.statusCode().value(),
                                                "Token Refresh Failed",
                                                response.headers().asHttpHeaders(),
                                                body.getBytes(),
                                                null
                                        ));
                                    });
                        })
                .bodyToMono(JwtResponse.class);
    }
}