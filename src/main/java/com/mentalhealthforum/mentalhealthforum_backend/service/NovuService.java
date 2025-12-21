package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.config.NovuProperties;
import com.mentalhealthforum.mentalhealthforum_backend.dto.NovuSubscriberRequest;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public class NovuService {

    private static final Logger log = LoggerFactory.getLogger(NovuService.class);
    private final WebClient webClient;

    public NovuService(
            WebClient.Builder webClientBuilder,
            NovuProperties novuProperties) {
        this.webClient = webClientBuilder
                .baseUrl(novuProperties.getBaseUrl())
                .defaultHeader("Authorization", "ApiKey " + novuProperties.getApiKey())
                .build();
    }

    public Mono<Void> upsertSubscriber(AppUser appUser){
        // Create the record
        var request = new NovuSubscriberRequest(
                appUser.getKeycloakId().toString(),
                appUser.getFirstName(),
                appUser.getLastName(),
                appUser.getEmail(),
                appUser.getAvatarUrl(),
                appUser.getLanguage(),
                null // Meta-data can be included here if needed
        );

        return webClient.post()
                .uri("/v1/subscribers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new RuntimeException("Novu sync Error: " + error)))
                )
                .toBodilessEntity()
                .doOnSuccess(v -> log.info("Novu subscriber Synced: {}", appUser.getKeycloakId()))
                .doOnError(e -> log.error("Novu Sync Failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty()) // Essential Don't break the user flow
                .then();
    }
}
