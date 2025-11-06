package com.productionsupport.service;

import com.productionsupport.model.OperationalRequest;
import com.productionsupport.model.OperationalResponse;
import com.productionsupport.model.OperationalResponse.RunbookStep;
import com.productionsupport.service.PatternClassifier.ClassificationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main orchestrator that coordinates classification and runbook retrieval
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionSupportOrchestrator {

    private final PatternClassifier patternClassifier;
    private final RunbookParser runbookParser;
    
    // Task names for display
    private static final Map<String, String> TASK_NAMES = Map.of(
        "CANCEL_CASE", "Cancel Case",
        "UPDATE_CASE_STATUS", "Update Case Status"
    );
    
    /**
     * Process an operational request and return next steps
     */
    public OperationalResponse processRequest(OperationalRequest request) {
        log.info("Processing request: {}", request.getQuery());
        
        // Step 1: Classify the request (or use explicit taskId if provided)
        String taskId;
        Map<String, String> entities;
        
        if (request.getTaskId() != null && !request.getTaskId().isEmpty()) {
            // Explicit task ID provided
            taskId = request.getTaskId();
            // Still need to extract entities
            ClassificationResult classification = patternClassifier.classify(request.getQuery());
            entities = classification.getEntities();
        } else {
            // Classify using pattern matching
            ClassificationResult classification = patternClassifier.classify(request.getQuery());
            taskId = classification.getTaskId();
            entities = classification.getEntities();
        }
        
        // Step 2: Get runbook steps for the classified task
        List<RunbookStep> steps = runbookParser.getSteps(taskId, null);
        
        // Step 3: Build warnings
        List<String> warnings = buildWarnings(taskId, entities);
        
        // Step 4: Build response
        return OperationalResponse.builder()
            .taskId(taskId)
            .taskName(TASK_NAMES.getOrDefault(taskId, taskId))
            .extractedEntities(entities)
            .steps(steps)
            .warnings(warnings)
            .build();
    }
    
    /**
     * Get steps for a specific stage (precheck, procedure, postcheck, rollback)
     */
    public List<RunbookStep> getStepsForStage(String taskId, String stage) {
        return runbookParser.getSteps(taskId, stage);
    }
    
    /**
     * Build warnings based on extracted entities and task
     */
    private List<String> buildWarnings(String taskId, Map<String, String> entities) {
        List<String> warnings = new ArrayList<>();
        
        if (!entities.containsKey("case_id")) {
            warnings.add("No case ID found in query. You'll need to provide it manually.");
        }
        
        if ("UPDATE_CASE_STATUS".equals(taskId) && !entities.containsKey("status")) {
            warnings.add("No target status found in query. You'll need to provide it manually.");
        }
        
        if ("CANCEL_CASE".equals(taskId)) {
            warnings.add("Case cancellation is a critical operation. Please review pre-checks carefully.");
        }
        
        return warnings;
    }
}

