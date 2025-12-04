package com.mentalhealthforum.mentalhealthforum_backend.enums;

import lombok.Getter;

@Getter
public enum RealmRole {
    FORUM_MEMBER("forum_member", "Basic forum access"),
    TRUSTED_MEMBER("trusted_member", "Trusted community member with additional privileges"),
    PEER_SUPPORTER("peer_supporter", "Can provide peer support"),
    MODERATOR("moderator", "Content moderation capabilities"),
    ADMIN("admin", "Full administrative access");

    private final String roleName;
    private final String roleDescription;

    RealmRole(String roleName, String roleDescription) {
        this.roleName = roleName;
        this.roleDescription = roleDescription;
    }

    public static RealmRole fromRoleName(String roleName){
        for(RealmRole role: values()){
            if(role.roleName.equals(roleName)){
                return role;
            }
        }
        return null;
    }

    public static boolean isValidRole(String roleName){
        return fromRoleName(roleName) != null;
    }
}
