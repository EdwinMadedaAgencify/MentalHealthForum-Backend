package com.mentalhealthforum.mentalhealthforum_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "frontend")
public class FrontendProperties {
    private String baseUrl;
}
