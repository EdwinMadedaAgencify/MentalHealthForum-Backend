package com.mentalhealthforum.mentalhealthforum_backend.enums.listings;

import lombok.Getter;

@Getter
public enum BookmarkSortField {
    TITLE("title"),
    BOOKMARKED_AT("bookmarked_at"),
    LAST_ACTIVITY_AT("last_activity_at"),
    POST_COUNT("post_count");

    private final String value;

    BookmarkSortField(String value) {
        this.value = value;
    }

    public static BookmarkSortField fromString(String value) {
        if(value == null){
            return BOOKMARKED_AT;  // Default to bookmarked_at
        }
        for(BookmarkSortField field : BookmarkSortField.values()){
            if(field.getValue().equalsIgnoreCase(value)){
                return field;
            }
        }
        return  BOOKMARKED_AT;
    }
}
