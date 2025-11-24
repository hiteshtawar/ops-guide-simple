package com.lca.productionsupport.model;

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
     * Downstream service that handles this task
     */
    private String downstreamService;
    
    /**
     * Extracted entities (case_id, status, etc.)
     */
    private Map<String, String> extractedEntities;
    
    /**
     * Steps grouped by type (prechecks, procedure, postchecks, rollback)
     */
    private StepGroups steps;
    
    /**
     * Any warnings or notes
     */
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepGroups {
        private List<RunbookStep> prechecks;
        private List<RunbookStep> procedure;
        private List<RunbookStep> postchecks;
        private List<RunbookStep> rollback;
    }

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
         * Name of the step
         */
        private String name;
        
        /**
         * Description of what this step does
         */
        private String description;
        
        /**
         * Step execution method (LOCAL_MESSAGE, HEADER_CHECK, GET, POST, PATCH, etc.)
         */
        private StepMethod method;
        
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
        
        /**
         * Headers to include in the HTTP request (from YAML definition)
         */
        private Map<String, String> headers;
    }
}

