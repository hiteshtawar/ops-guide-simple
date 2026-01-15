package com.lca.productionsupport.model;

/**
 * Enum representing the method type for runbook step execution
 */
public enum StepMethod {
    /**
     * Local execution: Returns a predefined message without any external calls
     */
    LOCAL_MESSAGE,
    
    /**
     * Local execution: Validates request headers without making downstream API calls
     */
    HEADER_CHECK,
    
    /**
     * Local execution: Validates extracted entities against their validation rules
     */
    ENTITY_VALIDATION,
    
    /**
     * Downstream execution: HTTP GET request
     */
    GET,
    
    /**
     * Downstream execution: HTTP POST request
     */
    POST,
    
    /**
     * Downstream execution: HTTP PUT request
     */
    PUT,
    
    /**
     * Downstream execution: HTTP PATCH request
     */
    PATCH,
    
    /**
     * Downstream execution: HTTP DELETE request
     */
    DELETE;
    
    /**
     * Check if this method is a local execution (doesn't require downstream service)
     */
    public boolean isLocalExecution() {
        return this == LOCAL_MESSAGE || this == HEADER_CHECK || this == ENTITY_VALIDATION;
    }
    
    /**
     * Check if this method is a downstream HTTP call
     */
    public boolean isDownstreamExecution() {
        return !isLocalExecution();
    }
    
    /**
     * Convert string to StepMethod enum, case-insensitive
     */
    public static StepMethod fromString(String method) {
        if (method == null) {
            return null;
        }
        try {
            return valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

