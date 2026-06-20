package com.mentalhealthforum.mentalhealthforum_backend.enums.listings;

import lombok.Getter;

@Getter
public enum TagSortField {
    NAME("name"),
    CREATED_AT("created_at"),
    USAGE("usage");

    private final String value;

    TagSortField(String value) {
        this.value = value;
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
}
