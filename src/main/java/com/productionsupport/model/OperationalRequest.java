package com.productionsupport.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Incoming request from user describing what they want to do
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalRequest {
    
    /**
     * Natural language description of what the user wants to do
     * Examples:
     * - "Cancel case CASE-2024-001"
     * - "Update case status to completed for CASE-2024-001"
     * - "Mark case CASE-2024-005 as in_progress"
     */
    @NotBlank(message = "Query is required and cannot be empty")
    private String query;
    
    /**
     * User ID making the request
     */
    private String userId;
    
    /**
     * Optional: Override automatic pattern detection
     * Allowed values: CANCEL_CASE, UPDATE_CASE_STATUS
     */
    private String taskId;
}

