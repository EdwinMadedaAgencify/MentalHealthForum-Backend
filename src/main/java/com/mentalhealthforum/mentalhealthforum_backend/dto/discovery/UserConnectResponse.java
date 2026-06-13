package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ConnectionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UserConnectResponse {

    // Connection metadata
    private UUID id;
    private ConnectionStatus status;
    private Boolean notificationEnabled;
    private Instant createdAt;

    // Initiator (who sent the request)
    private UUID initiatedById;
    private String initiatorDisplayName;
    private String initiatorAvatarUrl;
    private String initiatorBio;
    private Instant initiatorLastActiveAt;

    // Recipient (who received the request)
    private UUID recipientId;
    private String recipientDisplayName;
    private String recipientAvatarUrl;
    private String recipientBio;
    private Instant recipientLastActiveAt;

}
