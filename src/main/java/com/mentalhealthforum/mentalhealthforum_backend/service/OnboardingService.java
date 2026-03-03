package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding.OnboardingStatusResponse;
import org.apache.commons.lang3.concurrent.UncheckedFuture;
import reactor.core.publisher.Mono;

public interface OnboardingService {
    Mono<OnboardingStatusResponse> getOnboardingStatus(ViewerContext viewerContext);
}
