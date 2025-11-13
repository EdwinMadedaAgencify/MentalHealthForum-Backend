package com.mentalhealthforum.mentalhealthforum_backend.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mental Health Forum API")
                        .description("API documentation for the Mental Health Forum backend, including user management and forum services.")
                        .version("1.0.0"));
    }
}
