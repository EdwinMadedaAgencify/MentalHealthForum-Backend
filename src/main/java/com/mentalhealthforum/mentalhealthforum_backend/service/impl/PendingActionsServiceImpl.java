package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PendingActionsResponse;
import com.mentalhealthforum.mentalhealthforum_backend.service.ActionDescriptions;
import com.mentalhealthforum.mentalhealthforum_backend.service.KeycloakAdminManager;
import com.mentalhealthforum.mentalhealthforum_backend.service.PendingActionsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.List;

@Service
public class PendingActionsServiceImpl implements PendingActionsService {

    private static final Logger log = LoggerFactory.getLogger(PendingActionsServiceImpl.class);

    private final KeycloakAdminManager adminManager;

    public PendingActionsServiceImpl(KeycloakAdminManager adminManager) { this.adminManager = adminManager;}

    @Override
    public Mono<PendingActionsResponse> getPendingActions(String identifier) {
        return Mono.fromCallable(()-> adminManager.getRequiredActions(identifier))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optional -> Mono.just(optional.orElse(Collections.emptyList())))
                .map(actions -> new PendingActionsResponse(
                        identifier,
                        actions,
                        ActionDescriptions.describe(actions)
                ));
    }
}
