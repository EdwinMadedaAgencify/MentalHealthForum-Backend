package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
@Table("category_tags")
public class ForumCategoryTagEntity {
    @Id
    @Column("id")
    private UUID id;  // Let database generate via gen_random_uuid()

    @Column("category_id")
    private UUID categoryId ;

    @Column("tag_name ")
    private String tagName ;

    @Column("tag_description")
    private String tagDescription;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    public boolean isSameTag(ForumCategoryTagEntity other){
        if (other == null) return false;
        return this.categoryId.equals(other.categoryId) && this.tagName.equalsIgnoreCase(other.tagName);
    }
}
