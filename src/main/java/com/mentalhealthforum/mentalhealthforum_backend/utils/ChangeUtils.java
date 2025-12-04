package com.mentalhealthforum.mentalhealthforum_backend.utils;

import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Utility class for safely updating fields only if their values have changed.
 * Prevents unnecessary database updates and ensures proper Set copying.
 */
public final class ChangeUtils {

    private ChangeUtils() {
        // Prevent instantiation
    }

    // --- Strict version (Keycloak) ---
    public static <T> boolean setIfChangedStrict(T newValue, T currentValue, Consumer<T> setter) {
        if (newValue == null) return false; // ignore null
        if (!newValue.equals(currentValue)) {
            setter.accept(newValue);
            return true;
        }
        return false;
    }

    // --- Allow null/blank (user fields) ---
    public static boolean setIfChangedAllowNull(String newValue, String currentValue, Consumer<String> setter) {
        if (newValue != null) newValue = newValue.trim();  // Trim for consistency
        if (!Objects.equals(newValue, currentValue)) {
            setter.accept(newValue); // Allow null or blank, but trim to avoid spurious updates
            return true;
        }
        return false;
    }

    public static <T> boolean setIfChangedAllowNull(T newValue, T currentValue, Consumer<T> setter) {
        if (newValue == null && currentValue == null) return false; // Don't update if both are null
        if (!Objects.equals(newValue, currentValue)) {
            setter.accept(newValue);
            return true;
        }
        return false;
    }

    // --- String version ---
    public static boolean setIfChanged(String newValue, String currentValue, Consumer<String> setter) {
        if (newValue != null) newValue = newValue.trim();
        if (newValue != null && !newValue.isBlank() && !Objects.equals(newValue, currentValue)) {
            setter.accept(newValue);
            return true;
        }
        return false;
    }

    // --- Boolean version ---
    public static boolean setIfChanged(Boolean newValue, Boolean currentValue, Consumer<Boolean> setter) {
        if (!Objects.equals(newValue, currentValue)) {
            setter.accept(newValue);
            return true;
        }
        return false;
    }

    // --- Set<String> version ---
    public static boolean setIfChanged(Set<String> newValue, Set<String> currentValue, Consumer<Set<String>> setter) {
        Set<String> safeNew = (newValue == null) ? Set.of() : new HashSet<>(newValue);
        Set<String> safeCurrent = (currentValue == null) ? Set.of() : currentValue;

        if (!safeNew.equals(safeCurrent)) {
            setter.accept(safeNew);
            return true;
        }
        return false;
    }
}
