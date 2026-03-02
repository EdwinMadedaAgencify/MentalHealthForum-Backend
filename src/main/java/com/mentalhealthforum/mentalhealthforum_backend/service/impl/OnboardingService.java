package com.mentalhealthforum.mentalhealthforum_backend.service.impl;

import com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding.OnboardingStatusResponse;
import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import reactor.core.publisher.Mono;

public interface OnboardingService {
    Mono<OnboardingStatusResponse> getOnboardingStatus(ViewerContext viewerContext);
}
