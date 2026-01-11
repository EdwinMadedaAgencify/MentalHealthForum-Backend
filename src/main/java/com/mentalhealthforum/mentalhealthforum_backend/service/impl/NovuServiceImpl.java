package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.config.NovuProperties;
import com.mentalhealthforum.mentalhealthforum_backend.dto.notification.NotificationPreferences;
import com.mentalhealthforum.mentalhealthforum_backend.dto.novu.NovuPreferenceRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.novu.NovuSubscriberRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.novu.NovuTriggerRequest;
import com.mentalhealthforum.mentalhealthforum_backend.enums.NovuWorkflow;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUser;
import com.mentalhealthforum.mentalhealthforum_backend.service.NovuPayload;
import com.mentalhealthforum.mentalhealthforum_backend.service.NovuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class NovuServiceImpl implements NovuService {

    private static final Logger log = LoggerFactory.getLogger(NovuServiceImpl.class);
    private final WebClient webClient;

    public NovuServiceImpl(
            WebClient.Builder webClientBuilder,
            NovuProperties novuProperties) {
        this.webClient = webClientBuilder
                .baseUrl(novuProperties.getBaseUrl())
                .defaultHeader("Authorization", "ApiKey " + novuProperties.getApiKey())
                .build();
    }

    @Override
    public <T extends NovuPayload> Mono<Boolean> triggerEvent(
            NovuWorkflow workflow,
            String subscriberId,
            String targetEmail,
            T payload
    ) {

        // Safety check: Is the payload provided actually the one assigned to this workflow?
        // 1. Synchronous Safety Check (Happens immediately)
        if(!workflow.getPayloadClass().isInstance(payload)){
            throw new IllegalArgumentException(
                    String.format("Invalid payload for workflow %s. Expected %s but got %s",
                            workflow.name(),
                            workflow.getPayloadClass().getSimpleName(),
                            payload.getClass().getSimpleName())
            );
        }

        log.info("Triggering {} for subscriber {}", workflow.getWorkflowTrigger(), targetEmail);


        var request = NovuTriggerRequest.from(
                workflow.getWorkflowTrigger(),
                subscriberId,
                targetEmail,
                payload.toPayloadMap()
        );

        // 2. Return the Mono so the caller can react to the result
        return webClient.post()
                .uri("/events/trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(e -> Mono.error(new RuntimeException("Novu Trigger Error: " + e)))
                )
                .toBodilessEntity()
                .map(response -> true)
                .doOnSuccess(v -> log.info("Novu event Triggered: {} for user: {}", workflow.getWorkflowTrigger(), targetEmail))
                .onErrorResume(e -> {
                    log.error("Novu trigger failed: {}", e.getMessage());
                    return Mono.just(false); // Map failure to 'false' so the Admin knows
                });
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
                                .flatMap(e -> Mono.error(new RuntimeException("Novu sync Error: " + e)))
                )
                .toBodilessEntity()
                .doOnSuccess(v -> log.info("Novu subscriber Synced: {}", appUser.getKeycloakId()))
                .doOnError(e -> log.error("Novu Sync Failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty()) // Essential Don't break the user flow
                .then();
    }

    public Mono<Void> syncPreferences(String subscriberId, NotificationPreferences prefs){
        if(prefs == null) return Mono.empty();

        // Map your DTO to Novu's workflow Slugs
        // We create a list of updates for each category you defined
        List<NovuPreferenceRequest> updates = List.of(
            createPrefRequest("replies", prefs.getInApp().getReplies(), prefs.getEmail().getReplies()),
            createPrefRequest("reactions", prefs.getInApp().getModeration(), prefs.getEmail().getModeration()),
            createPrefRequest("follows", prefs.getInApp().getFollows(), prefs.getEmail().getFollows()),
            createPrefRequest("moderation", prefs.getInApp().getModeration(), prefs.getEmail().getModeration())
        );

        // Send them to Novu sequentially or in parallel
        return Flux.fromIterable(updates)
                .flatMap(update -> webClient.patch()
                        .uri("/subscribers/{subscriberId}/preferences/{templateId}", subscriberId, update.templateId())
                        .bodyValue(update)
                        .retrieve()
                        .toBodilessEntity()
                ).then()
                .doOnSuccess(v -> log.info("Synched preferences for subscriber: {}", subscriberId))
                .onErrorResume(e -> {
                    log.error("Preference sync failed {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private NovuPreferenceRequest createPrefRequest(
            String slug,
            Boolean inApp,
            Boolean email) {
        return new NovuPreferenceRequest(
                slug,
                false, // workflow is enabled
                List.of(
                        new NovuPreferenceRequest.ChannelPreference("in_app", inApp),
                        new NovuPreferenceRequest.ChannelPreference("email", email)
                )
        );
    };

}
