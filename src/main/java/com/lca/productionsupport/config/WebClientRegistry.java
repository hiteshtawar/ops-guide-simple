package com.lca.productionsupport.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry for managing multiple WebClient instances for different downstream services
 */
@Slf4j
@Component
public class WebClientRegistry {
    
    private final Map<String, WebClient> webClients = new HashMap<>();
    private final DownstreamServiceProperties serviceProperties;
    
    public WebClientRegistry(DownstreamServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
        initializeWebClients();
    }
    
    /**
     * Initialize WebClients for all configured downstream services
     */
    private void initializeWebClients() {
        serviceProperties.getServices().forEach((serviceName, config) -> {
            log.info("Initializing WebClient for service: {} with base URL: {}", 
                    serviceName, config.getBaseUrl());
            
            WebClient webClient = WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .build();
            
            webClients.put(serviceName, webClient);
        });
    }
    
    /**
     * Get WebClient for a specific service
     * @param serviceName The name of the service (e.g., "ap-services")
     * @return WebClient for the service
     * @throws IllegalArgumentException if service is not configured
     */
    public WebClient getWebClient(String serviceName) {
        WebClient webClient = webClients.get(serviceName);
        
        if (webClient == null) {
            throw new IllegalArgumentException(
                "No WebClient configured for service: " + serviceName + ". " +
                "Available services: " + webClients.keySet()
            );
        }
        
        return webClient;
    }
    
    /**
     * Get timeout for a specific service
     */
    public Duration getTimeout(String serviceName) {
        DownstreamServiceProperties.ServiceConfig config = serviceProperties.getServices().get(serviceName);
        if (config == null) {
            return Duration.ofSeconds(30); // default
        }
        return Duration.ofSeconds(config.getTimeout());
    }
    
    /**
     * Check if a service is configured
     */
    public boolean hasService(String serviceName) {
        return webClients.containsKey(serviceName);
    }
    
    /**
     * Get all configured service names
     */
    public java.util.Set<String> getServiceNames() {
        return webClients.keySet();
    }
}

