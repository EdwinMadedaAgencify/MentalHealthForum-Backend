package com.mentalhealthforum.mentalhealthforum_backend.dto.discovery;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ConnectionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class UserConnectResponse {

    private UUID id;
    private UUID initiatedById;
    private String initiatedByDisplayName;
    private UUID recipientId;
    private String recipientDisplayName;
    private ConnectionStatus status;
    private Boolean notificationEnabled;
    private Instant createdAt;
    private Instant updatedAt;

}
