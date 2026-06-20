package com.mentalhealthforum.mentalhealthforum_backend.enums.listings;

import lombok.Getter;

@Getter
public enum PostSortField {

    CREATED_AT("created_at"),
    UPDATED_AT("updated_at");

    private final String value;

    PostSortField(String value) {
        this.value = value;
    }

    public static PostSortField fromString(String value) {
        if(value == null){
            return CREATED_AT;  // Default to createdAt
        }
        for(PostSortField field : PostSortField.values()){
            if(field.getValue().equalsIgnoreCase(value)){
                return field;
            }
        }
        return CREATED_AT;
    }

}
