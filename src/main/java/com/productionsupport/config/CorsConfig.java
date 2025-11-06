package com.productionsupport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration to allow frontend access
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow credentials (cookies, authorization headers, etc.)
        config.setAllowCredentials(true);
        
        // Allow frontend origins
        config.setAllowedOrigins(Arrays.asList(
            "http://localhost:5173",  // Vite default
            "http://localhost:3000",  // React/Next.js default
            "http://localhost:4200"   // Angular default
        ));
        
        // Allow all headers
        config.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow all HTTP methods
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // Expose headers that the frontend might need
        config.setExposedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Total-Count"
        ));
        
        // Cache preflight requests for 1 hour
        config.setMaxAge(3600L);
        
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}

