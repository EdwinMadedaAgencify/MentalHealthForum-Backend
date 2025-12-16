package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.PendingActionsResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.service.PendingActionsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/auth")
public class AuthStatusController {

    private final PendingActionsService pendingActionsService;

    public AuthStatusController(PendingActionsService pendingActionsService) {
        this.pendingActionsService = pendingActionsService;
    }

    @GetMapping("/pending-actions")
    public Mono<ResponseEntity<StandardSuccessResponse<PendingActionsResponse>>> getPendingActions(
            @RequestParam String identifier){

        return pendingActionsService.getPendingActions(identifier)
                .map(response ->{
                    var success = new StandardSuccessResponse<>(
                            "Pending actions retrieved successfully",
                            response
                    );
                    return ResponseEntity.ok(success);
                });
    }
}
