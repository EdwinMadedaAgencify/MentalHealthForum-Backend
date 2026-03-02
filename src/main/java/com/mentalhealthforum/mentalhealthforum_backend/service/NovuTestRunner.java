package com.mentalhealthforum.mentalhealthforum_backend.service;

import com.mentalhealthforum.mentalhealthforum_backend.dto.novu.ManualTriggerTestPayload;
import com.mentalhealthforum.mentalhealthforum_backend.enums.NovuWorkflow;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class NovuTestRunner implements CommandLineRunner {


    private final NovuService novuService;

    public NovuTestRunner(NovuService novuService) {
        this.novuService = novuService;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("🚀 Firing test notification to Novu...");

        ManualTriggerTestPayload payload = new ManualTriggerTestPayload("Alvin Okello");

        novuService.triggerEvent(NovuWorkflow.MANUAL_TRIGGER_TEST, "edwin-test-003", null, payload)
                .subscribe(
                response -> System.out.println("✅ Novu success: " + response),
                error -> System.err.println("❌ Novu failure: " + error.getMessage())
        );

    }
}


