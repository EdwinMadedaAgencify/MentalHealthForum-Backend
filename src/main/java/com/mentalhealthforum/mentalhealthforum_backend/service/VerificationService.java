package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.VerificationDto;
import com.mentalhealthforum.mentalhealthforum_backend.enums.VerificationType;
import jakarta.validation.constraints.NotBlank;
import reactor.core.publisher.Mono;

public interface VerificationService {
    /**
     * Entry point for Admin/Registration services.
     * Coordinates token generation and return for the magic link.
     */
    Mono<String> createVerificationLink(String email, VerificationType type, String groupPath, String newValue);

    Mono<Void> requestNewVerificationLink(String email);

    /**
     * Entry point for the Controller.
     * Coordinates validation and finalization of the user account.
     */
    Mono<VerificationDto> processVerification(String token, String email);
}
