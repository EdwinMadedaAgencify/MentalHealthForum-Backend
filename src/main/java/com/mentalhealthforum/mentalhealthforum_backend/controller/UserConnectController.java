package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PaginatedResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.UserConnectRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.discovery.UserConnectResponse;
import com.mentalhealthforum.mentalhealthforum_backend.service.JwtClaimsExtractor;
import com.mentalhealthforum.mentalhealthforum_backend.service.UserConnectService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/connections")
public class UserConnectController {

    private final UserConnectService userConnectService;
    private final JwtClaimsExtractor jwtClaimsExtractor;


    public UserConnectController(
            UserConnectService userConnectService,
            JwtClaimsExtractor jwtClaimsExtractor) {
        this.userConnectService = userConnectService;
        this.jwtClaimsExtractor = jwtClaimsExtractor;
    }

    @PostMapping("{userId}/request")
    public Mono<ResponseEntity<StandardSuccessResponse<UserConnectResponse>>> requestConnection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userConnectService.requestConnection(userId, viewerContext)
                .map(connect -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(new StandardSuccessResponse<>("Connection request sent successfully", connect)));
    }

    @PostMapping("/requests/{requesterId}/accept")
    public Mono<ResponseEntity<StandardSuccessResponse<UserConnectResponse>>> acceptConnection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID requesterId
            ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userConnectService.acceptConnection(requesterId, viewerContext)
                .map(connect -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("Connection request accepted", connect)));
    }

    @DeleteMapping("/requests/{requesterId}/decline")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> declineConnection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID requesterId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userConnectService.declineConnection(requesterId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(
                        new StandardSuccessResponse<>("Connection request declined"))));
    }

    @DeleteMapping("/{userId}/terminate")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> terminateConnection(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userConnectService.terminateConnection(userId, viewerContext)
                .then(Mono.just(ResponseEntity.ok(
                        new StandardSuccessResponse<>("Connection terminated successfully"))));
    }

    // ==================== STATUS & COUNTS ====================

    @GetMapping("/{userId}/connected")
    public Mono<ResponseEntity<StandardSuccessResponse<Boolean>>> areConnected(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        UUID currentUserId = UUID.fromString(viewerContext.getUserId());
        return userConnectService.areConnected(currentUserId, userId)
                .map(connected -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("Connection status retrieved successfully", connected)));
    }

    @GetMapping("/count")
    public Mono<ResponseEntity<StandardSuccessResponse<Long>>> getMyConnectionCount(
            @AuthenticationPrincipal Jwt jwt
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        UUID currentUserId = UUID.fromString(viewerContext.getUserId());
        return userConnectService.getConnectionCount(currentUserId)
                .map(count -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("Connection status retrieved successfully", count)));
    }

    @GetMapping("/pending/count")
    public Mono<ResponseEntity<StandardSuccessResponse<Long>>> getPendingRequestsCount(
            @AuthenticationPrincipal Jwt jwt
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        UUID currentUserId = UUID.fromString(viewerContext.getUserId());
        return userConnectService.getPendingRequestsCount(currentUserId)
                .map(count -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("Connection status retrieved successfully", count)));
    }

    // ==================== PAGINATED LISTS ====================

    @GetMapping
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<UserConnectResponse>>>> getMyConnections(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Parameter(name = "page", description = "Page number (0-indexed)", example = "0") int page,
            @RequestParam(defaultValue = "20") @Parameter(name = "size", description = "Number of items per page", example = "20") int size,
            @RequestParam(defaultValue = "") @Parameter(name = "search", description = "Search by other user's display name", example = "john") String search,  // ✅ Fixed
            @RequestParam(defaultValue = "created_at", name = "sort_by")
            @Parameter(name = "sort_by", description = "Sort field: created_at, display_name", example = "created_at") String sortBy,
            @RequestParam(required = false, name = "sort_direction")
            @Parameter(name = "sort_direction", description = "Sort direction: asc (ascending) or desc (descending)", example = "desc") String sortDirection
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userConnectService.getMyConnections(page, size, search, sortBy, sortDirection, viewerContext)
                .map(connections -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("Connections retrieved successfully", connections)));
    }

    @GetMapping("/pending")
    public Mono<ResponseEntity<StandardSuccessResponse<PaginatedResponse<UserConnectResponse>>>> getMyPendingRequests(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Parameter(name = "page", description = "Page number (0-indexed)", example = "0") int page,
            @RequestParam(defaultValue = "20") @Parameter(name = "size", description = "Number of items per page", example = "20") int size,
            @RequestParam(defaultValue = "") @Parameter(name = "search", description = "Search by other user's display name", example = "john") String search,  // ✅ Fixed
            @RequestParam(defaultValue = "incoming") @Parameter(name = "type", description = "Filter by request direction: incoming (requests sent to me), outgoing (requests I sent), all (both)", example = "incoming") String type,  // ✅ Fixed - more descriptive
            @RequestParam(defaultValue = "created_at", name = "sort_by")
            @Parameter(name = "sort_by", description = "Sort field: created_at, display_name", example = "created_at") String sortBy,
            @RequestParam(required = false, name = "sort_direction")
            @Parameter(name = "sort_direction", description = "Sort direction: asc (ascending) or desc (descending)", example = "desc") String sortDirection
    ){
        ViewerContext viewerContext = jwtClaimsExtractor.extractViewerContext(jwt);
        return userConnectService.getMyPendingRequests(page, size, search, type, sortBy, sortDirection, viewerContext)
                .map(connections -> ResponseEntity.ok(
                        new StandardSuccessResponse<>("pending requests retrieved successfully", connections)));
    }
}
