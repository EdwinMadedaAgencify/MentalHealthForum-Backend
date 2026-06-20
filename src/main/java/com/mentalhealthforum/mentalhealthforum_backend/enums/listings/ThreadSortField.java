package com.mentalhealthforum.mentalhealthforum_backend.enums.listings;

import lombok.Getter;

@Getter
public enum ThreadSortField {
    CREATED_AT("created_at"),
    LAST_ACTIVITY_AT("last_activity_at"),
    POST_COUNT("post_count"),
    VIEW_COUNT("view_count"),
    TITLE("title");

    private final String value;

    ThreadSortField(String value) {
        this.value = value;
    }

    public static ThreadSortField fromString(String value) {
        if(value == null){
            return LAST_ACTIVITY_AT;  // Default to most recent activity
        }
        for(ThreadSortField field : ThreadSortField.values()){
            if(field.getValue().equalsIgnoreCase(value)){
                return field;
            }
        }
        return  LAST_ACTIVITY_AT;
    }

}
