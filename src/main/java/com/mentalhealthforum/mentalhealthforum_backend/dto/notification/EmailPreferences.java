package com.mentalhealthforum.mentalhealthforum_backend.dto.notification;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailPreferences {
    @NotNull(message = "Replies email preference must not be null.")
    private Boolean replies = false;

    @NotNull(message = "Reactions email preference must not be null.")
    private Boolean reactions = false;

    @NotNull(message = "Follows email preference must not be null.")
    private Boolean follows = false;

    @NotNull(message = "Moderation email preference must not be null.")
    private Boolean moderation = true;

    @NotNull(message = "System email preference must not be null.")
    private Boolean system = false;
}
