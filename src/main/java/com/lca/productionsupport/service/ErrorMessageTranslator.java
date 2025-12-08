package com.lca.productionsupport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service to translate technical error messages into user-friendly messages
 */
@Slf4j
@Service
public class ErrorMessageTranslator {
    
    private ErrorMappingConfig config;
    
    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            ClassPathResource resource = new ClassPathResource("error-messages.yaml");
            config = mapper.readValue(resource.getInputStream(), ErrorMappingConfig.class);
            log.info("Loaded {} error message mappings", config.getErrorMappings().size());
        } catch (IOException e) {
            log.error("Failed to load error-messages.yaml, using default fallback", e);
            // Fallback to default configuration
            config = getDefaultConfig();
        }
    }
    
    /**
     * Translate technical error message to user-friendly message
     * @param technicalError The technical error message
     * @return Translation result containing user-friendly message and technical details
     */
    public TranslationResult translate(String technicalError) {
        if (technicalError == null || technicalError.isEmpty()) {
            return new TranslationResult(
                "An error occurred while processing your request.",
                null,
                "UNKNOWN_ERROR"
            );
        }
        
        // Try to match against patterns
        for (ErrorMapping mapping : config.getErrorMappings()) {
            try {
                Pattern pattern = Pattern.compile(mapping.getPattern(), Pattern.CASE_INSENSITIVE);
                if (pattern.matcher(technicalError).find()) {
                    log.debug("Matched error pattern: {} for error: {}", mapping.getPattern(), technicalError);
                    return new TranslationResult(
                        mapping.getUserMessage(),
                        technicalError,
                        mapping.getCategory()
                    );
                }
            } catch (Exception e) {
                log.warn("Invalid regex pattern: {}", mapping.getPattern(), e);
            }
        }
        
        // Fallback if no pattern matched
        return new TranslationResult(
            "An unexpected error occurred while processing your request.",
            technicalError,
            "UNKNOWN_ERROR"
        );
    }
    
    /**
     * Default configuration used when YAML file cannot be loaded
     */
    private ErrorMappingConfig getDefaultConfig() {
        ErrorMappingConfig defaultConfig = new ErrorMappingConfig();
        defaultConfig.setErrorMappings(List.of(
            new ErrorMapping(
                "Connection refused.*",
                "Unable to connect to the downstream service. The service may be unavailable.",
                "CONNECTION_ERROR"
            ),
            new ErrorMapping(
                ".*timeout.*",
                "The operation took too long to complete. Please try again.",
                "TIMEOUT_ERROR"
            ),
            new ErrorMapping(
                ".*",
                "An unexpected error occurred. Please contact support.",
                "UNKNOWN_ERROR"
            )
        ));
        return defaultConfig;
    }
    
    /**
     * Configuration class for error mappings
     */
    @Data
    public static class ErrorMappingConfig {
        private List<ErrorMapping> errorMappings;
    }
    
    /**
     * Individual error mapping
     */
    @Data
    public static class ErrorMapping {
        private String pattern;
        private String userMessage;
        private String category;
        
        public ErrorMapping() {}
        
        public ErrorMapping(String pattern, String userMessage, String category) {
            this.pattern = pattern;
            this.userMessage = userMessage;
            this.category = category;
        }
    }
    
    /**
     * Result of error message translation
     */
    @Data
    public static class TranslationResult {
        private final String userFriendlyMessage;
        private final String technicalDetails;
        private final String errorCategory;
        
        public TranslationResult(String userFriendlyMessage, String technicalDetails, String errorCategory) {
            this.userFriendlyMessage = userFriendlyMessage;
            this.technicalDetails = technicalDetails;
            this.errorCategory = errorCategory;
        }
    }
}

