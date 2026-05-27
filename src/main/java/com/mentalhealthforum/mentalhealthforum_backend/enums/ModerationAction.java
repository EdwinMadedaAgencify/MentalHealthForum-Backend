package com.mentalhealthforum.mentalhealthforum_backend.enums;

import com.mentalhealthforum.mentalhealthforum_backend.dto.ViewerContext;
import com.mentalhealthforum.mentalhealthforum_backend.exception.error.ApiException;
import lombok.Getter;
import reactor.core.publisher.Mono;

@Getter
public enum ModerationAction {
    // Content actions
    POST_DELETED("Delete post", GroupPath.MODERATORS),
    POST_CONTENT_WARNING_ADDED("Add content warning", GroupPath.MODERATORS),
    POST_RESTORED("Restore post", GroupPath.MODERATORS),
    POST_PERMANENTLY_DELETED("Permanently delete post", GroupPath.ADMINISTRATORS),
    VIEW_DELETED_POSTS("View deleted posts", GroupPath.MODERATORS),

    // Thread actions
    THREAD_LOCKED("Lock thread", GroupPath.MODERATORS),
    THREAD_UNLOCKED("Unlock thread", GroupPath.MODERATORS),
    THREAD_SOFT_DELETED("Soft delete thread", GroupPath.MODERATORS),
    THREAD_RESTORED("Restore thread", GroupPath.MODERATORS),
    THREAD_MOVED("Move thread", GroupPath.MODERATORS),
    THREAD_MERGED("Merge threads", GroupPath.ADMINISTRATORS),
    THREAD_SPLIT("Split thread", GroupPath.ADMINISTRATORS),
    THREAD_ARCHIVED("Archive thread", GroupPath.MODERATORS),
    THREAD_UNARCHIVED("Unarchive thread", GroupPath.MODERATORS),
    THREAD_FEATURED("Feature thread", GroupPath.ADMINISTRATORS),
    THREAD_UNFEATURED("Un-feature thread", GroupPath.ADMINISTRATORS),
    THREAD_TYPE_CHANGED("Change thread type", GroupPath.MODERATORS),
    THREAD_STICKY_TOGGLED("Toggle sticky status", GroupPath.MODERATORS),
    THREAD_BEST_ANSWER_SET("Set best answer", GroupPath.MODERATORS),
    THREAD_BEST_ANSWER_CLEARED("Clear best answer", GroupPath.MODERATORS),
    THREAD_PERMANENTLY_DELETED("Permanently delete thread", GroupPath.ADMINISTRATORS),
    THREAD_METADATA_EDITED("Edit thread metadata", GroupPath.MODERATORS),
    THREAD_CONTENT_WARNING_ADDED("Add content warning to thread", GroupPath.MODERATORS),
    VIEW_DELETED_THREADS("View deleted threads", GroupPath.MODERATORS),

    // Category actions
    CATEGORY_CREATED("Create category", GroupPath.ADMINISTRATORS),
    CATEGORY_UPDATED("Update category", GroupPath.ADMINISTRATORS),
    CATEGORY_SOFT_DELETED("Soft Delete category", GroupPath.ADMINISTRATORS),
    CATEGORY_REACTIVATED("Reactivate category", GroupPath.ADMINISTRATORS),
    CATEGORY_PURGED("Purge category", GroupPath.ADMINISTRATORS),
    CATEGORY_VIEW_INACTIVE("View inactive categories", GroupPath.ADMINISTRATORS),
    CATEGORY_ACCESS_CHANGED("Change category access", GroupPath.ADMINISTRATORS),
    CATEGORY_PURGE_OLD("Purge old inactive categories", GroupPath.ADMINISTRATORS),

    // Tag actions
    CATEGORY_TAG_ADDED("Add tag to category", GroupPath.ADMINISTRATORS),
    CATEGORY_TAG_REMOVED("Remove tag from category", GroupPath.ADMINISTRATORS),
    CATEGORY_TAG_UPDATED("Update tag description", GroupPath.ADMINISTRATORS),
    CATEGORY_TAG_REPLACED("Replace all tags", GroupPath.ADMINISTRATORS),


    // User actions
    USER_WARNED("Warn user", GroupPath.MODERATORS),
    USER_MUTED("Mute user", GroupPath.MODERATORS),
    USER_UNMUTED("Unmute user", GroupPath.MODERATORS),
    USER_SUSPENDED("Suspend user", GroupPath.ADMINISTRATORS),
    USER_UNSUSPENDED("Unsuspend user", GroupPath.ADMINISTRATORS),
    USER_BANNED("Ban user", GroupPath.ADMINISTRATORS),
    USER_UNBANNED("Unban user", GroupPath.ADMINISTRATORS),
    USER_REPUTATION_ADJUSTED("Adjust reputation", GroupPath.ADMINISTRATORS),

    // Role/permission changes
    ROLE_GRANTED("Grant role", GroupPath.ADMINISTRATORS),
    ROLE_REVOKED("Revoke role", GroupPath.ADMINISTRATORS),
    GROUP_ADDED("Add to group", GroupPath.ADMINISTRATORS),
    GROUP_REMOVED("Remove from group", GroupPath.ADMINISTRATORS),

    // Report handling
    REPORT_ASSIGNED("Assign report", GroupPath.MODERATORS),
    REPORT_ESCALATED("Escalate report", GroupPath.MODERATORS),
    REPORT_ACTIONED("Action report", GroupPath.MODERATORS),
    REPORT_DISMISSED("Dismiss report", GroupPath.MODERATORS),
    REPORT_DETAILS_UPDATED("Update report details", GroupPath.MODERATORS),

    // System/bulk actions
    BULK_ACTION("Bulk action", GroupPath.ADMINISTRATORS);


    private final String displayName;
    private final GroupPath requiredGroup;


    ModerationAction(String displayName, GroupPath requiredGroup) {
        this.displayName = displayName;
        this.requiredGroup = requiredGroup;
    }

    public boolean isAllowedFor(ViewerContext viewerContext){
        return viewerContext.isInGroup(requiredGroup);
    }

    public Mono<Void> checkPermission(ViewerContext viewerContext){
        if (!isAllowedFor(viewerContext)) {  // ✅ If NOT allowed → error
            return Mono.error(new ApiException(
                    String.format("You do not have permission to %s. This action requires %s group.",
                            getDisplayName().toLowerCase(),
                            requiredGroup.getDisplayName()),
                    ErrorCode.FORBIDDEN
            ));
        }
        return Mono.empty();
    }

}
