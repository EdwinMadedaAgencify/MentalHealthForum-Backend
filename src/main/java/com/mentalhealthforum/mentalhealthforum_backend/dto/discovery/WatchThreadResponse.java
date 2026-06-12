package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WatchThreadResponse {

    private UUID id;
    private UUID userId;
    private UUID threadId;
    private UUID threadTitle;
    private Boolean notificationEnabled;
    private Instant watchedAt;

}
