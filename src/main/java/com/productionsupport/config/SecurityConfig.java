package com.productionsupport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for JWT-based authentication
 * Roles/permissions are extracted from JWT token (no database needed)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * Security configuration for production (JWT-based)
     */
    @Bean
    @Profile("prod")
    public SecurityFilterChain prodSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/v1/health", "/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {
                    // JWT validation will be handled by spring-security-oauth2-resource-server
                    // Roles will be extracted from JWT claims
                })
            );

        return http.build();
    }

    /**
     * Development security configuration (disabled for easier testing)
     */
    @Bean
    @Profile({"dev", "default"})
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll() // Allow all requests in dev mode
            );

        return http.build();
    }
}

