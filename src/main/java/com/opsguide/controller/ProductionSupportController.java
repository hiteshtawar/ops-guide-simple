package com.opsguide.controller;

import com.opsguide.model.OperationalRequest;
import com.opsguide.model.OperationalResponse;
import com.opsguide.model.OperationalResponse.RunbookStep;
import com.opsguide.model.StepExecutionRequest;
import com.opsguide.model.StepExecutionResponse;
import com.opsguide.service.ProductionSupportOrchestrator;
import com.opsguide.service.StepExecutionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Main REST API controller for Production Support
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductionSupportController {

    private final ProductionSupportOrchestrator orchestrator;
    private final StepExecutionService stepExecutionService;
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
    
    /**
     * Main endpoint: Process an operational request
     * 
     * Example requests:
     * {
     *   "query": "Cancel case CASE-2024-001",
     *   "userId": "user123"
     * }
     * 
     * {
     *   "query": "Update case CASE-2024-001 status to completed",
     *   "userId": "user123"
     * }
     */
    @PostMapping("/process")
    public ResponseEntity<OperationalResponse> processRequest(@Valid @RequestBody OperationalRequest request) {
        log.info("Received request: {}", request.getQuery());
        
        OperationalResponse response = orchestrator.processRequest(request);
        
        if ("UNKNOWN".equals(response.getTaskId())) {
            log.warn("Could not classify request: {}", request.getQuery());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get steps for a specific task and stage
     * 
     * GET /api/v1/tasks/CANCEL_CASE/steps?stage=precheck
     */
    @GetMapping("/tasks/{taskId}/steps")
    public ResponseEntity<List<RunbookStep>> getSteps(
        @PathVariable String taskId,
        @RequestParam(required = false) String stage
    ) {
        log.info("Getting steps for task: {}, stage: {}", taskId, stage);
        
        List<RunbookStep> steps = orchestrator.getStepsForStage(taskId, stage);
        
        return ResponseEntity.ok(steps);
    }
    
    /**
     * Execute a specific step
     * 
     * POST /api/v1/execute-step
     * {
     *   "taskId": "CANCEL_CASE",
     *   "stepNumber": 1,
     *   "entities": {"case_id": "CASE-2024-001"},
     *   "userId": "user123",
     *   "authToken": "your-jwt-token"
     * }
     */
    @PostMapping("/execute-step")
    public ResponseEntity<StepExecutionResponse> executeStep(@Valid @RequestBody StepExecutionRequest request) {
        log.info("Executing step {} for task {}", request.getStepNumber(), request.getTaskId());
        
        StepExecutionResponse response = stepExecutionService.executeStep(request);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Classify a query without getting full runbook
     * 
     * POST /api/v1/classify
     * {
     *   "query": "Cancel case CASE-2024-001"
     * }
     */
    @PostMapping("/classify")
    public ResponseEntity<OperationalResponse> classifyOnly(@Valid @RequestBody OperationalRequest request) {
        log.info("Classifying query: {}", request.getQuery());
        
        // Process but only return classification, not full steps
        OperationalResponse response = orchestrator.processRequest(request);
        
        // Clear steps to return lightweight response
        response.setSteps(null);
        
        return ResponseEntity.ok(response);
    }
}
