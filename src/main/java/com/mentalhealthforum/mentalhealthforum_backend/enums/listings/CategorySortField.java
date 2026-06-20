package com.mentalhealthforum.mentalhealthforum_backend.enums.listings;

import lombok.Getter;

@Getter
public enum CategorySortField {
    SORT_ORDER("sort_order"),
    NAME("name"),
    CREATED_AT("created_at");

    private final String value;

    CategorySortField(String value) {
        this.value = value;
    }

    public static CategorySortField fromString(String value) {
        if(value == null){
            return SORT_ORDER;  // Default to sort_order
        }
        for(CategorySortField field : CategorySortField.values()){
            if(field.getValue().equalsIgnoreCase(value)){
                return field;
            }
        }
        return  SORT_ORDER;
    }
}
