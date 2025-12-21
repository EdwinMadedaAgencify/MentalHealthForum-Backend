package com.mentalhealthforum.mentalhealthforum_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class NovuTestRunner implements CommandLineRunner {

    @Autowired
    private NotificationService notificationService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🚀 Firing test notification to Novu...");
        notificationService.triggerNotification("edwin-test-003", "Edwin");
    }
}
