package com.lca.productionsupport.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from executing a step
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepExecutionResponse {
    
    /**
     * Whether the step succeeded
     */
    private Boolean success;
    
    /**
     * Step number executed
     */
    private Integer stepNumber;
    
    /**
     * Step description
     */
    private String stepDescription;
    
    /**
     * HTTP status code from API call
     */
    private Integer statusCode;
    
    /**
     * Response body from API call
     */
    private String responseBody;
    
    /**
     * Any error message
     */
    private String errorMessage;
    
    /**
     * API error message extracted from responseBody (simplified for user display)
     */
    private String apiErrorMessage;
    
    /**
     * Step response message generated from verification (e.g., "Audit Log entry was created by user123 for caseId 2025123P6732 and status was changed to canceled")
     */
    private String stepResponse;
    
    /**
     * Time taken in milliseconds
     */
    private Long durationMs;
}

