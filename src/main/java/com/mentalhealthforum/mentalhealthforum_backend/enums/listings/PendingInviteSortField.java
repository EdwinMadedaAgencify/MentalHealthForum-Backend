package com.mentalhealthforum.mentalhealthforum_backend.enums.listings;

import lombok.Getter;

@Getter
public enum PendingInviteSortField {
    USERNAME("username"),
    EMAIL("email"),
    DATE_CREATED("date_created");

    private final String value;

    PendingInviteSortField(String value) {
        this.value = value;
    }

    public static PendingInviteSortField fromString(String value) {
        if(value == null){
            return DATE_CREATED;  // Default to date created
        }
        for(PendingInviteSortField field : PendingInviteSortField.values()){
            if(field.getValue().equalsIgnoreCase(value)){
                return field;
            }
        }
        return  DATE_CREATED;
    }

}
