package com.mentalhealthforum.mentalhealthforum_backend.exception.error;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ErrorDetail;
import com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding.OnboardingPolicy;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ErrorCode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown when a user attempts to update their profile or sync,
 * but fails the specific requirements for their role (e.g., bio length).
 */
public class OnboardingPolicyViolationException extends  ApiException{

    public OnboardingPolicyViolationException(List<OnboardingPolicy.Violation> violations) {
        super(
                ErrorCode.VALIDATION_FAILED.getDescription(),
                ErrorCode.VALIDATION_FAILED,
                mapViolations(violations),
                null);
    }

    private static List<ErrorDetail> mapViolations(List<OnboardingPolicy.Violation> violations) {
        return violations.stream()
                .map(violation -> new ErrorDetail(violation.field(), violation.message()))
                .collect(Collectors.toList());
    }
}
