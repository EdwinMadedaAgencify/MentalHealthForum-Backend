package com.mentalhealthforum.mentalhealthforum_backend.dto.notification;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InAppPreferences {
    @NotNull(message = "Replies in-app preference must not be null.")
    private Boolean replies = true;

    @NotNull(message = "Reactions in-app preference must not be null.")
    private Boolean reactions = true;

    @NotNull(message = "Follows in-app preference must not be null.")
    private Boolean follows = true;

    @NotNull(message = "Moderation in-app preference must not be null.")
    private Boolean moderation = true;

    @NotNull(message = "System in-app preference must not be null.")
    private Boolean system = true;
}
