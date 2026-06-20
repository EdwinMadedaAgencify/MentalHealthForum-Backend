package com.mentalhealthforum.mentalhealthforum_backend.enums.listings;

import lombok.Getter;

@Getter
public enum AppUserSortField {
    DISPLAY_NAME("display_name"),
    DATE_JOINED("date_joined"),
    POST_COUNT("posts_count"),
    REPUTATION_SCORE("reputation_score"),
    LAST_POSTED_AT("last_posted_at"),
    LAST_ACTIVITY_AT("last_active_at");

    private final String value;

    AppUserSortField(String value) {
        this.value = value;
    }

    public static AppUserSortField fromString(String value) {
        if(value == null){
            return DISPLAY_NAME;  // Default to alphabetical
        }
        for(AppUserSortField field : AppUserSortField.values()){
            if(field.getValue().equalsIgnoreCase(value)){
                return field;
            }
        }
        return  DISPLAY_NAME;
    }

}
