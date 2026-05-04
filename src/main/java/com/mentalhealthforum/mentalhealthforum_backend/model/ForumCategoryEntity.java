package com.mentalhealthforum.mentalhealthforum_backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.DefaultThreadSettings;
import com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged.ParticipationRequirements;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import com.mentalhealthforum.mentalhealthforum_backend.utils.JsonUtils;
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
@Table("forum_categories")
public class ForumCategoryEntity {
    @Id
    @Column("id")
    private UUID id;  // Let database generate via gen_random_uuid()

    @Column("name")
    private String name;

    @Column("slug")
    private String slug;

    @Column("description")
    private String description;

    @Column("color_theme")
    private String colorTheme;

    @Column("parent_category_id")
    private UUID parentCategoryId;

    @Column("participation_requirements")
    private JsonNode participationRequirementsJson;

    @Column("content_warning_type")
    private ContentWarningType contentWarningType;

    @Column("content_warning_custom_text")
    private String contentWarningCustomText;

    @Column("default_thread_settings")
    private JsonNode defaultThreadSettingsJson;

    @Column("is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column("sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    // --- Getter/Setter using JsonUtils
    public ParticipationRequirements getParticipationRequirements() {
        if (participationRequirementsJson == null || participationRequirementsJson.isEmpty()) {
            return new ParticipationRequirements();
        }
        return JsonUtils.jsonNodeToObject(participationRequirementsJson, ParticipationRequirements.class);
    }

    public void setParticipationRequirements(ParticipationRequirements requirements){
        this.participationRequirementsJson = JsonUtils.objectToJsonNode(
                requirements == null? new ParticipationRequirements() : requirements
        );
    }

    public DefaultThreadSettings getDefaultThreadSettings() {
        if (defaultThreadSettingsJson == null || defaultThreadSettingsJson.isEmpty()) {
            return new DefaultThreadSettings();
        }
        return JsonUtils.jsonNodeToObject(defaultThreadSettingsJson, DefaultThreadSettings.class);
    }

    public void setDefaultThreadSettings(DefaultThreadSettings defaultThreadSettings){
        this.defaultThreadSettingsJson = JsonUtils.objectToJsonNode(
                defaultThreadSettings == null? new DefaultThreadSettings() : defaultThreadSettings
        );
    }

    // --- Helper methods ---
    public boolean isParent(){
        return parentCategoryId == null;
    }

    public boolean isChild(){
        return parentCategoryId != null;
    }
}
