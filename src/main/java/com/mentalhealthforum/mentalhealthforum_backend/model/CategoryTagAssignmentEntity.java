package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@Table("category_tag_assignments")
public class CategoryTagAssignmentEntity {
    @Id
    @Column("id")
    private UUID id;

    @Column("category_id")
    private UUID categoryId;

    @Column("tag_id")
    private UUID tagId;

    @Column("assigned_by")
    private UUID assignedBy;

    @CreatedDate
    @Column("assigned_at")
    private Instant assignedAt;

}
