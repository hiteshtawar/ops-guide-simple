package com.productionsupport.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response containing classification and next steps
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalResponse {
    
    /**
     * The classified task ID
     */
    private String taskId;
    
    /**
     * Human-readable task name
     */
    private String taskName;
    
    /**
     * Extracted entities (case_id, status, etc.)
     */
    private Map<String, String> extractedEntities;
    
    /**
     * Steps to execute from the runbook
     */
    private List<RunbookStep> steps;
    
    /**
     * Any warnings or notes
     */
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunbookStep {
        /**
         * Step number (1-indexed)
         */
        private Integer stepNumber;
        
        /**
         * Description of what this step does
         */
        private String description;
        
        /**
         * HTTP method (GET, POST, PATCH, etc.)
         */
        private String method;
        
        /**
         * API endpoint path
         */
        private String path;
        
        /**
         * Request body (if applicable)
         */
        private String requestBody;
        
        /**
         * Expected response
         */
        private String expectedResponse;
        
        /**
         * Whether this step can be auto-executed
         */
        private Boolean autoExecutable;
        
        /**
         * Step type: precheck, procedure, postcheck, rollback
         */
        private String stepType;
    }
}

