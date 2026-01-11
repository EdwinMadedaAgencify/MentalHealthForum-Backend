package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.notification.NotificationPreferences;
import com.mentalhealthforum.mentalhealthforum_backend.enums.NovuWorkflow;
import com.mentalhealthforum.mentalhealthforum_backend.model.AppUser;
import reactor.core.publisher.Mono;

public interface NovuService {
    /**
     * Dispatches a notification via the Novu trigger system.
     * * @param workflow The specific notification event to trigger.
     *
     * @param email The unique identifier of the recipient (usually email).
     * @param payload      The type-safe data required by the specific workflow.
     * @return boolean
     */
    <T extends NovuPayload> Mono<Boolean> triggerEvent(
            NovuWorkflow workflow,
            String subscriberId,
            String email,
            T payload
    );

    /**
     * Creates or updates a subscriber's profile information in Novu.
     */
    Mono<Void> upsertSubscriber(AppUser appUser);

    /**
     * Synchronizes local notification preferences with Novu's settings.
     */
    Mono<Void> syncPreferences(String subscriberId, NotificationPreferences prefes);
}
