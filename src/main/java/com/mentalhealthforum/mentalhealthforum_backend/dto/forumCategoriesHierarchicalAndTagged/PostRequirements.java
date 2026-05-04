package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import com.mentalhealthforum.mentalhealthforum_backend.enums.GroupPath;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostRequirements {

    @Builder.Default
    private Integer minReputation = null;

    @Builder.Default
    private Set<GroupPath> requiredGroups = new HashSet<>();

    @Builder.Default
    private Integer minAccountAgeDays = null;

    @Builder.Default
    private Integer maxPostsPerDay = null;
}
