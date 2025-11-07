package com.lca.productionsupport.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for downstream services
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "downstream-services")
public class DownstreamServiceProperties {
    
    /**
     * Map of service name to service config
     * e.g., "ap-services" -> ServiceConfig{baseUrl, timeout}
     */
    private Map<String, ServiceConfig> services = new HashMap<>();
    
    // Convenience getters for known services
    public ServiceConfig getApServices() {
        return services.get("ap-services");
    }
    
    @Data
    public static class ServiceConfig {
        private String baseUrl;
        private Integer timeout = 30; // default 30 seconds
    }
}

