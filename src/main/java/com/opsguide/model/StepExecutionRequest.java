package com.opsguide.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request to execute a specific step
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepExecutionRequest {
    
    /**
     * The task ID (CANCEL_CASE, UPDATE_CASE_STATUS)
     */
    @NotBlank(message = "Task ID is required")
    private String taskId;
    
    /**
     * Step number to execute
     */
    @NotNull(message = "Step number is required")
    private Integer stepNumber;
    
    /**
     * Extracted entities (case_id, status, etc.)
     */
    private Map<String, String> entities;
    
    /**
     * User ID making the request
     */
    private String userId;
    
    /**
     * Authorization token for downstream API calls
     */
    private String authToken;
}

