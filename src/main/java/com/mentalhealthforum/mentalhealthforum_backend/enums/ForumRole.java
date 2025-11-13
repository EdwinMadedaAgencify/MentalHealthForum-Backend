package com.mentalhealthforum.mentalhealthforum_backend.enums;

public enum ForumRole {
    FORUM_MEMBER("forum_member"),
    TRUSTED_MEMBER("trusted_member"),
    PEER_SUPPORTER("peer_supporter"),
    MODERATOR("moderator"),
    ADMIN("admin");

    private final String roleName;

    ForumRole(String roleName) {
        this.roleName = roleName;
    }

    public String getRoleName(){
        return roleName;
    }
}
