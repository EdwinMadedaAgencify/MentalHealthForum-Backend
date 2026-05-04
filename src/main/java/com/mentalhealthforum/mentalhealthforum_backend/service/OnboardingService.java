package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.dto.userProfileAndIdentity.onboarding.OnboardingStatusResponse;
import reactor.core.publisher.Mono;

public interface OnboardingService {
    Mono<OnboardingStatusResponse> getOnboardingStatus(ViewerContext viewerContext);
}
