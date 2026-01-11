package com.mentalhealthforum.mentalhealthforum_backend.controller;

import com.mentalhealthforum.mentalhealthforum_backend.dto.RequestNewVerificationLinkRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.StandardSuccessResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.VerificationRequest;
import com.mentalhealthforum.mentalhealthforum_backend.dto.VerificationDto;
import com.mentalhealthforum.mentalhealthforum_backend.enums.VerificationType;
import com.mentalhealthforum.mentalhealthforum_backend.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
public class VerificationController {

    private static final Logger log = LoggerFactory.getLogger(VerificationController.class);

    private final VerificationService verificationService;

    /**
     * Endpoint hit by Next.js frontend when a user clicks the magic link.
     */
    @PostMapping("/verify")
    public Mono<ResponseEntity<StandardSuccessResponse<VerificationDto>>> verify(
            @RequestBody VerificationRequest verificationRequest,
            ServerWebExchange exchange
    ){

        final URI currentUri = exchange.getRequest().getURI();

        return verificationService.processVerification(verificationRequest.token(), verificationRequest.email())
                .map(data ->  {

                    StandardSuccessResponse<VerificationDto> response =
                            new StandardSuccessResponse<>( "Verification successful.", data);

                    String userId = data.metadata().userId();

                    // Build location pointing to the NEWLY CREATED user profile
                    URI location = UriComponentsBuilder.fromUri(currentUri)
                            .replacePath("/api/users/${userId}")
                            .buildAndExpand(userId)
                            .toUri(); // Finalizes the URI object

                    return ResponseEntity.ok().location(location).body(response);
                })
                .switchIfEmpty(Mono.defer(()-> {
                    log.error("Verification stream completed empty for token: {}", verificationRequest.token());
                    return Mono.error(new IllegalStateException("Verification failed to produce a result"));
                }));
    }

    @PostMapping("/request-link")
    public Mono<ResponseEntity<StandardSuccessResponse<Void>>> requestNewVerificationLink(
            @RequestBody RequestNewVerificationLinkRequest request){
        return verificationService.requestNewVerificationLink(request.email())
                .thenReturn(ResponseEntity.ok(new StandardSuccessResponse<>(
                        "If an account is pending, a new verification email has been sent."
                )));
    }
}
