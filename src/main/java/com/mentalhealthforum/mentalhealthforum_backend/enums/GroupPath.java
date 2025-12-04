package com.mentalhealthforum.mentalhealthforum_backend.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
public enum GroupPath {
    // Root Groups
    MEMBERS("/members", "General members"),
    MODERATORS("/moderators", "Content moderators"),
    ADMINISTRATORS("/administrators", "Administrators"),

    // Subgroups - Members
    MEMBERS_NEW("/members/new", "New members"),
    MEMBERS_ACTIVE("/members/active", "Active members"),
    MEMBERS_TRUSTED("/members/trusted", "Trusted members"),

    // Subgroups - Moderators
    MODERATORS_PEER("/moderators/peer", "Peer moderators"),
    MODERATORS_PROFESSIONAL("/moderators/professional", "Professional moderators");

    private final String path;
    private final String description;

    GroupPath(String path, String description){
        this.path = path;
        this.description = description;
    }

    public static GroupPath fromPath(String path){
        for(GroupPath group: values()){
            if(group.path.equals(path)){
                return group;
            }
        }
        return null;
    }

    public static boolean isInGroup(String userGroupPath, GroupPath targetGroup){
        if(userGroupPath == null || targetGroup == null) return false;

        // For administrators, exact match
        if(targetGroup == ADMINISTRATORS){
            return userGroupPath.equals(targetGroup.getPath());
        }

        // For other groups, check if user's group starts with the target group path
        return userGroupPath.startsWith(targetGroup.getPath());
    }

    public static List<GroupPath> getSubgroups(GroupPath parentGroup){
        return Arrays.stream(values())
                .filter(group ->group.path.startsWith(parentGroup.getPath() + "/"))
                .toList();
    }

    // Helper method to check if a path contains a group
    public static boolean pathContainsGroup(String userGroupPath, GroupPath targetGroup){
        if(userGroupPath == null || targetGroup == null) return false;
        return userGroupPath.contains(targetGroup.getPath());
    }
}
