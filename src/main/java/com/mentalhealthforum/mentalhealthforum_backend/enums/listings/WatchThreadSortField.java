package com.mentalhealthforum.mentalhealthforum_backend.enums.listings;

import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.SortOption;
import lombok.Getter;

@Getter
public enum WatchThreadSortField {
    CREATED_AT("created_at", "created at", "DESC"),
    LAST_ACTIVITY_AT("last_activity_at", "last activity at", "DESC"),
    THREAD_TITLE("thread_title", "thread title", "ASC"),
    POST_COUNT("post_count", "post count", "DESC"),
    VIEW_COUNT("view_count", "view count", "DESC");

    private final String value;
    private final String label;
    private final String defaultDirection;

    WatchThreadSortField(String value, String label, String defaultDirection) {
        this.value = value;
        this.label = label;
        this.defaultDirection = defaultDirection;
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

    public String determineSortDirection(String sortDirection){
        if(sortDirection != null){
            return "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
        }
        return this.defaultDirection;
    }

    public SortOption toSortOption(){
        return SortOption.builder()
                .value(this.value)
                .label(this.label)
                .defaultDirection(this.defaultDirection)
                .build();
    }

}
