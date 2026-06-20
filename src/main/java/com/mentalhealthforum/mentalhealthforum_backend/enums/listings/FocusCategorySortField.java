package com.mentalhealthforum.mentalhealthforum_backend.enums.listings;

import lombok.Getter;

@Getter
public enum FocusCategorySortField {
   CREATED_AT("created_at"),
   CATEGORY_NAME("category_name");

    private final String value;

    FocusCategorySortField(String value) {
        this.value = value;
    }

    public static FocusCategorySortField fromString(String value) {
        if(value == null){
            return CREATED_AT;  // Default to created_at
        }
        for(FocusCategorySortField field : FocusCategorySortField.values()){
            if(field.getValue().equalsIgnoreCase(value)){
                return field;
            }
        }
        return CREATED_AT;
    }

}
