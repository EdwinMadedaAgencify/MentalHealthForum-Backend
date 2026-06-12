package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ConnectionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_connections")
public class UserConnectEntity {

    @Id
    private UUID id;

    @Column("user_1")
    private UUID user1; // Always the smaller UUID (pure storage)

    @Column("user_2")
    private UUID user2;  // Always the larger UUID (pure storage)

    @Column("initiated_by")
    private UUID initiatedBy; // Who actually initiated the request

    @Column("status")
    @Builder.Default
    private ConnectionStatus status = ConnectionStatus.PENDING;

    @Column("notification_enabled")
    @Builder.Default
    private Boolean notificationEnabled = true;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private Instant updatedAt;


    // ==================== HELPER METHODS ====================

    public boolean isInitiated(UUID userId){
        return initiatedBy != null && initiatedBy.equals(userId);
    }

    public UUID getRecipient(){
        return this.getUser1().equals(initiatedBy)
                ? this.getUser2()
                : this.getUser1();
    }
}
