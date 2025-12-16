package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.enums.RequiredAction;

import java.util.List;

public class ActionDescriptions {
    public static List<String> describe(List<String> actions){
        return actions.stream()
                .map(action ->  RequiredAction.fromKeycloak(action)
                        .map(RequiredAction::getUserMessage)
                        .orElse("Additional actions are required in your account mono.")
                )
                .toList();
    }
}
