package com.mentalhealthforum.mentalhealthforum_backend.dto.novu;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO for triggering a Novu workflow via REST API.
 * Ref: <a href="https://docs.novu.co/api-reference/events/trigger-event">...</a>
 */
public record NovuTriggerRequest(
        String name, // The Workflow ID/Slug from Novu Dashboard (e.g., "admin-invite")
        Map<String, Object> to, // The subscriberId (your Keycloak ID)
        Map<String, Object> payload // Dynamic data: { "tempPassword": "...", "link": "..." }
) {
    /**
     * Static factory to create a request with JIT Subscriber details.
     * This keeps the 'how' of Novu's subscriber format hidden from the service.
     */
    public static NovuTriggerRequest from(String workflowName, String subscriberId, String email, Map<String, Object> payloadMap) {
        Map<String, Object> subscriber = new HashMap<>(Map.of(
                "subscriberId", subscriberId
                // You can add firstName here if you want to extend this later
        ));

        if(email != null && !email.isBlank()){
            subscriber.put("email", email);
        }

        return new NovuTriggerRequest(workflowName, subscriber, payloadMap);
    }
}
