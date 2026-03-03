package com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding;

import com.mentalhealthforum.mentalhealthforum_backend.enums.OnboardingStage;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Map;

public record OnboardingStatusResponse(
        @Nullable OnboardingStage onboardingStage,
        boolean isSynced,
        boolean isPolicySatisfied,
        List<OnboardingPolicy.Violation> violations,
        List<OnboardingPolicy.FieldRequirement> requirements,
        String message,
        Map<String, Object> metadata
) {}