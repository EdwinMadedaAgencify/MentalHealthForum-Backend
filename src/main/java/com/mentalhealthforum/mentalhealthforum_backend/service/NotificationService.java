package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.config.NovuProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class NotificationService {

    private final WebClient webClient;

    public NotificationService(
            NovuProperties novuProperties,
            WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(novuProperties.getBaseUrl())
                .defaultHeader("Authorization","ApiKey " + novuProperties.getApiKey())
                .build();

    }

    public void triggerNotification(String userId, String name) {
        Map<String, Object> body = Map.of(
                "name", "test-notification",
                "to", Map.of("subscriberId", userId),
                "payload", Map.of("name", name)
        );

        System.out.println("📤 Sending Payload: " + body);

        this.webClient.post()
                .uri("/events/trigger")
                .bodyValue(body)
                .retrieve()
                // Handle HTTP errors (4xx, 5xx)
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    System.err.println("❌ Novu API Error Body: " + errorBody);
                                    return Mono.error(new RuntimeException("Novu API error: " + errorBody));
                                })
                )
                .bodyToMono(String.class)
                .subscribe(
                        response -> System.out.println("✅ Novu success: " + response),
                        error -> System.err.println("❌ WebClient failure: " + error.getMessage())
                );
    }
}
