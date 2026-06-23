package com.mentalhealthforum.mentalhealthforum_backend.enums.listings;

import com.mentalhealthforum.mentalhealthforum_backend.dto.filters.SortOption;
import lombok.Getter;

@Getter
public enum TagSortField {
    NAME("name", "name", "ASC"),
    CREATED_AT("created_at", "created at", "DESC"),
    USAGE("usage", "usage", "DESC");

    private final String value;
    private final String label;
    private final String defaultDirection;

    TagSortField(String value, String label, String defaultDirection) {
        this.value = value;
        this.label = label;
        this.defaultDirection = defaultDirection;
    }

    public static TagSortField fromString(String value){
        if(value == null){
            return NAME; // Default to name
        }
        for(TagSortField field: TagSortField.values()){
            if(field.getValue().equalsIgnoreCase(value)){
                return field;
            }
        }
        return NAME;
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
