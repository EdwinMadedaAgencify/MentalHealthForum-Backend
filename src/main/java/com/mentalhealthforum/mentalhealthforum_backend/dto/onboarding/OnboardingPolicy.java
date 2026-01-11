package com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding;

import java.util.List;

public record OnboardingPolicy() {
    public record Violation(String field, String message) {}

    public record Result(boolean isSatisfied, List<Violation> violations) {
        public static Result success() { return new Result(true, List.of()); }
        public static Result failure(List<Violation> violations) {
            return new Result(false, violations);
        }
    }
}
