package com.mentalhealthforum.mentalhealthforum_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("focus_categories")
public class FocusCategoryEntity {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("category_id")
    private UUID categoryId;

    @Column("notification_enabled")
    @Builder.Default
    private Boolean notificationEnabled = true;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

}
