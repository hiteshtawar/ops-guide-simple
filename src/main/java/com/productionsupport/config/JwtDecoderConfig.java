package com.productionsupport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import javax.crypto.spec.SecretKeySpec;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT Decoder configuration for dev/local testing
 * Uses a symmetric key (HS256) to validate test tokens
 */
@Configuration
public class JwtDecoderConfig {

    /**
     * JWT decoder for dev/local profiles using symmetric key
     * Accepts test JWT tokens signed with HS256
     */
    @Bean
    @Profile({"dev", "local", "default"})
    public JwtDecoder jwtDecoderForDev() {
        // Test secret key (same one used to sign the test JWT)
        String secret = "test-secret-key-for-development-minimum-256-bits-required-for-hs256-algorithm";
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        
        return NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }

    /**
     * JWT Authentication Converter - extracts roles from JWT and adds ROLE_ prefix
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Extract roles from JWT
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) {
                return Collections.emptyList();
            }
            
            // Convert to GrantedAuthority with ROLE_ prefix
            return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
        });
        
        return converter;
    }
}

