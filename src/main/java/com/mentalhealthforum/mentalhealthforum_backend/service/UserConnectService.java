package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.UserConnectResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserConnectService {
    Mono<UserConnectResponse> requestConnection(UUID userId, ViewerContext viewerContext);

    Mono<UserConnectResponse> acceptConnection(UUID requesterId, ViewerContext viewerContext);

    Mono<Void> declineConnection(UUID requesterId, ViewerContext viewerContext);

    Mono<Void> terminateConnection(UUID connectedUserId, ViewerContext viewerContext);

    Mono<Boolean> areConnected(UUID userId1, UUID userId2);

    Mono<PaginatedResponse<UserConnectResponse>> getMyConnections(
            int page,
            int size,
            Boolean notificationEnabled,
            String search,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext);

    Mono<PaginatedResponse<UserConnectResponse>> getMyPendingRequests(
            int page,
            int size,
            String search,
            String type,
            String sortBy,
            String sortDirection,
            ViewerContext viewerContext
    );

    Mono<Long> getConnectionCount(UUID userId);

    Mono<Long> getPendingRequestsCount(UUID userId);
}
