package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.enums.OtpPurpose;
import reactor.core.publisher.Mono;

public interface OtpWorker {

    Mono<String> generateAndSaveOtp(String email, OtpPurpose purpose);

    Mono<Void> verifyOtp(String email, String rawCode, OtpPurpose purpose);
}
