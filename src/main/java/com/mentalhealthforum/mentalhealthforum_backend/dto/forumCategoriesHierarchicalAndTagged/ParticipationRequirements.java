package com.mentalhealthforum.mentalhealthforum_backend.dto.forumCategoriesHierarchicalAndTagged;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mentalhealthforum.mentalhealthforum_backend.enums.ViewAccess;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParticipationRequirements {

    @Builder.Default
    private ViewAccess viewAccess = ViewAccess.MEMBERS_ONLY;

    @Builder.Default
    private PostRequirements postRequirements = new PostRequirements();

}
