package com.opsguide.model;

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
     * Time taken in milliseconds
     */
    private Long durationMs;
}

