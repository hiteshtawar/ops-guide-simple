package com.lca.productionsupport.service;

import com.lca.productionsupport.model.UseCaseDefinition.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts entities from natural language queries using regex patterns defined in YAML
 */
@Slf4j
@Service
public class RunbookEntityExtractor {
    
    /**
     * Extract entities from query based on extraction configuration
     */
    public Map<String, String> extract(String query, ExtractionConfig config) {
        Map<String, String> entities = new HashMap<>();
        
        if (config == null || config.getEntities() == null) {
            return entities;
        }
        
        for (Map.Entry<String, EntityConfig> entry : config.getEntities().entrySet()) {
            String entityName = entry.getKey();
            EntityConfig entityConfig = entry.getValue();
            
            String value = extractEntity(query, entityConfig);
            
            if (value != null) {
                entities.put(entityName, value);
                log.debug("Extracted entity '{}': {}", entityName, value);
            } else if (entityConfig.isRequired()) {
                log.warn("Required entity '{}' not found in query: {}", entityName, query);
            }
        }
        
        log.debug("Extracted entities: {}", entities);
        return entities;
    }
    
    private String extractEntity(String query, EntityConfig config) {
        if (config.getPatterns() == null || config.getPatterns().isEmpty()) {
            return null;
        }
        
        for (String patternStr : config.getPatterns()) {
            try {
                Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(query);
                
                if (matcher.find()) {
                    String value = matcher.group(1);
                    
                    // Apply transformations
                    value = applyTransform(value, config.getTransform());
                    
                    // Validate
                    if (validate(value, config.getValidation())) {
                        return value;
                    }
                }
            } catch (Exception e) {
                log.error("Pattern matching failed for pattern: {}", patternStr, e);
            }
        }
        
        return null;
    }
    
    private String applyTransform(String value, String transform) {
        if (transform == null || value == null) {
            return value;
        }
        
        return switch (transform.toLowerCase()) {
            case "lowercase" -> value.toLowerCase();
            case "uppercase" -> value.toUpperCase();
            case "trim" -> value.trim();
            default -> value;
        };
    }
    
    private boolean validate(String value, ValidationConfig validation) {
        if (validation == null) {
            return true;
        }
        
        // Regex validation
        if (validation.getRegex() != null) {
            if (!value.matches(validation.getRegex())) {
                log.warn("Validation failed: {} doesn't match regex {}", 
                        value, validation.getRegex());
                return false;
            }
        }
        
        // Enum validation
        if (validation.getEnumValues() != null) {
            boolean valid = validation.getEnumValues().stream()
                .anyMatch(enumVal -> enumVal.equalsIgnoreCase(value));
            
            if (!valid) {
                log.warn("Validation failed: {} not in allowed values {}", 
                        value, validation.getEnumValues());
                return false;
            }
        }
        
        return true;
    }
}

