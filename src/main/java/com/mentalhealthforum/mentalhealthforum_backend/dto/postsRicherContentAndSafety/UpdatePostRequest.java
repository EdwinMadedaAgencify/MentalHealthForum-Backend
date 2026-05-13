package com.mentalhealthforum.mentalhealthforum_backend.dto.postsRicherContentAndSafety;

import com.mentalhealthforum.mentalhealthforum_backend.enums.ContentWarningType;
import com.mentalhealthforum.mentalhealthforum_backend.enums.EditReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePostRequest {

    @NotNull(message = "Post content is required")
    @Size(min = 1, max = 1000, message = "Content must be between 1 and 10000 characters")
    private String content;

    @NotNull(message = "Edit reason is required")
    private EditReason editReason;

    private String editReasonCustomText; // Required when editReason = OTHER

    private ContentWarningType contentWarningType;

    private String contentWarningCustomText;
    
}
