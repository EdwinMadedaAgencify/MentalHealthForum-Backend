package com.mentalhealthforum.mentalhealthforum_backend.enums.listings;

import lombok.Getter;

@Getter
public enum WatchThreadSortField {
    CREATED_AT("created_at"),
    LAST_ACTIVITY_AT("last_activity_at"),
    THREAD_TITLE("thread_title"),
    POST_COUNT("post_count"),
    VIEW_COUNT("view_count");


    private final String value;

    WatchThreadSortField(String value) {
        this.value = value;
    }

    public static WatchThreadSortField fromString(String value) {
        if(value == null){
            return CREATED_AT;  // Default to created_at
        }
        for(WatchThreadSortField field : WatchThreadSortField.values()){
            if(field.getValue().equalsIgnoreCase(value)){
                return field;
            }
        }
        return  CREATED_AT;
    }

}
