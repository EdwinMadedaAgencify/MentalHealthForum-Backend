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
@Table("thread_bookmarks")
public class ThreadBookmarkEntity {

    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("thread_id")
    private UUID threadId;

    @Column("notes")
    private String notes;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

}
