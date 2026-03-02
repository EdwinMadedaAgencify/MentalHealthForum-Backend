package com.mentalhealthforum.mentalhealthforum_backend.dto.onboarding;

import java.util.List;

public record OnboardingPolicy() {
    public record Violation(String field, String message) {}

    public record Result(
            boolean isSatisfied,
            List<Violation> violations,
            List<FieldRequirement> requirements) {
        public static Result success(List<FieldRequirement> requirements) {
            return new Result(true, List.of(), requirements);
        }

        public static Result failure(List<Violation> violations, List<FieldRequirement> requirements) {
            return new Result(false, violations, requirements);
        }
    }

   public record FieldRequirement(
            String field,
            boolean required,
            Integer minLength,
            String description
    ) {}

}
