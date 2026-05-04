package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DefaultThreadSettings {

    @Builder.Default
    private Integer autoLockAfterDays = 90;

    @Builder.Default
    private Integer autoArchiveAfterDays = 180;

    @Builder.Default
    private Boolean requireModeratorApproval = false;

    @Builder.Default
    private Integer maxPostsPerThread = 500;

    @Builder.Default
    private Boolean allowAnonymousPosts = false;

}
