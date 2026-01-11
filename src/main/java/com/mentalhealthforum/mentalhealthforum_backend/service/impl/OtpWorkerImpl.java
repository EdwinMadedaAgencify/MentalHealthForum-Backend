package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.enums.OtpPurpose;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.InvalidTokenException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.TokenExpiredException;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.TooManyRequestsException;
import com.mentalhealthforum.mentalhealthforum_backend.model.OtpCredential;
import com.mentalhealthforum.mentalhealthforum_backend.repository.OtpCredentialRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
public class OtpWorkerImpl implements  OtpWorker{
    private final OtpCredentialRepository otpCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final Duration OTP_EXPIRY = Duration.ofMinutes(10); // OTPs should be short


    public OtpWorkerImpl(
            OtpCredentialRepository otpCredentialRepository,
            PasswordEncoder passwordEncoder) {
        this.otpCredentialRepository = otpCredentialRepository;
        this.passwordEncoder = passwordEncoder;
    }


    @Override
    public Mono<String> generateAndSaveOtp(String email, OtpPurpose purpose){
        return otpCredentialRepository.findByEmailAndPurpose(email, purpose)
                .flatMap(existingOtp -> {
                    // If the existing OTP was created LESS than 60 seconds ago, reject
                    if(existingOtp.getCreatedAt().isAfter(Instant.now().minus(Duration.ofSeconds(60)))){
                        return Mono.error(new TooManyRequestsException("Please wait 60 seconds before requesting a new code."));
                    }
                    return otpCredentialRepository.delete(existingOtp);
                })
                .then(Mono.defer(() -> {
                    String rawCode = String.format("%06d", secureRandom.nextInt(1000000));
                    OtpCredential newOtp = new OtpCredential(
                            null,
                            email,
                            passwordEncoder.encode(rawCode),
                            purpose,
                            Instant.now().plus(OTP_EXPIRY)
                    );
                    return otpCredentialRepository.save(newOtp).thenReturn(rawCode);
        }));
    }

    @Override
    public Mono<Void> verifyOtp(String email, String rawCode, OtpPurpose purpose){
        return otpCredentialRepository.findByEmailAndPurpose(email, purpose)
                // If no OTP found, throw an error
                .switchIfEmpty(Mono.error(new InvalidTokenException()))
                .flatMap(otpCredential -> {
                    // Check the expiry
                    if(otpCredential.isExpired()){
                        return otpCredentialRepository.delete(otpCredential)
                                .then(Mono.error(new TokenExpiredException()));
                    }
                    // Check the Bycrpt match
                    if(!passwordEncoder.matches(rawCode, otpCredential.getCodeHash())){
                        return Mono.error(new InvalidTokenException());
                    }

                    //Success - Delete the OTP so it's "Single Use"
                    return otpCredentialRepository.delete(otpCredential);
                });
    }

}
