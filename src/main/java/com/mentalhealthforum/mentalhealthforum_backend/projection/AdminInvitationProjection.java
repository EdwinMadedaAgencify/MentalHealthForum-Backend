package com.mentalhealthforum.mentalhealthforum_backend.projection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.UUID;

public interface AdminInvitationProjection {
    // Columns from admin_invitations (ai.*)
    UUID getKeycloakId();
    String getEmail();
    String getUsername();
    String getFirstName();
    String getLastName();
    String[] getGroups(); // R2DBC handles TEXT[] to String[]
    Boolean getIsEnabled();
    Boolean getIsEmailVerified();
    Instant getDateCreated();

    // Column from the JOIN (au.display_name)
    @Value("#{target['inviter_name']}")
    String getInviterName();
}