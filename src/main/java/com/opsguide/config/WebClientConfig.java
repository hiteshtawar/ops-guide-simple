package com.opsguide.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${case-management.api.base-url:https://api.example.com/v2}")
    private String baseUrl;

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .build();
    }
}

