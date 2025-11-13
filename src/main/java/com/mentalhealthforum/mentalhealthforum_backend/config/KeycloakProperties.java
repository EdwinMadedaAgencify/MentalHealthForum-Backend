package com.mentalhealthforum.mentalhealthforum_backend.config;

import com.mentalhealthforum.mentalhealthforum_backend.config.keycloak.Credentials;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {
    private String realm;
    private String authServerUrl;
    private String resource;
    private Credentials credentials;
}
