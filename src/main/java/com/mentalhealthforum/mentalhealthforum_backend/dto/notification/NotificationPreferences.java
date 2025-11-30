package com.mentalhealthforum.mentalhealthforum_backend.dto.notification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationPreferences {
    private InAppPreferences inApp;
    private EmailPreferences email;

    public NotificationPreferences() {
        this.inApp = new InAppPreferences();
        this.email = new EmailPreferences();
    }
}
