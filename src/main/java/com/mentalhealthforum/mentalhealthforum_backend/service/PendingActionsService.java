package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.authStatus.PendingActionsResponse;
import reactor.core.publisher.Mono;

public interface PendingActionsService {
    Mono<PendingActionsResponse> getPendingActions(String username);
}
