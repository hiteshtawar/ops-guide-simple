package com.productionsupport.service;

import com.productionsupport.model.OperationalRequest;
import com.productionsupport.model.OperationalResponse;
import com.productionsupport.model.OperationalResponse.RunbookStep;
import com.productionsupport.model.TaskType;
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
    
    /**
     * Process an operational request and return next steps
     */
    public OperationalResponse processRequest(OperationalRequest request) {
        log.info("Processing request: {}", request.getQuery());
        
        // Step 1: Classify the request (or use explicit taskId if provided)
        TaskType taskType;
        Map<String, String> entities;
        
        if (request.getTaskId() != null && !request.getTaskId().isEmpty()) {
            // Explicit task ID provided
            taskType = TaskType.fromString(request.getTaskId());
            // Still need to extract entities
            ClassificationResult classification = patternClassifier.classify(request.getQuery());
            entities = classification.getEntities();
        } else {
            // Classify using pattern matching
            ClassificationResult classification = patternClassifier.classify(request.getQuery());
            taskType = classification.getTaskType();
            entities = classification.getEntities();
        }
        
        // Step 2: Get runbook steps for the classified task
        List<RunbookStep> steps = runbookParser.getSteps(taskType.name(), null);
        
        // Step 3: Build warnings
        List<String> warnings = buildWarnings(taskType, entities);
        
        // Step 4: Build response
        return OperationalResponse.builder()
            .taskId(taskType.name())
            .taskName(taskType.getDisplayName())
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
     * Get all available task types
     * Useful for UI to display options when pattern detection fails
     */
    public List<Map<String, String>> getAvailableTasks() {
        List<Map<String, String>> tasks = new ArrayList<>();
        
        for (TaskType taskType : TaskType.values()) {
            if (taskType != TaskType.UNKNOWN) {
                Map<String, String> task = Map.of(
                    "taskId", taskType.name(),
                    "taskName", taskType.getDisplayName(),
                    "description", getTaskDescription(taskType)
                );
                tasks.add(task);
            }
        }
        
        return tasks;
    }
    
    /**
     * Get description for each task type
     */
    private String getTaskDescription(TaskType taskType) {
        return switch (taskType) {
            case CANCEL_CASE -> "Cancel a pathology case. Type: cancel case 2025P1234 and hit Send";
            case UPDATE_CASE_STATUS -> "Update case workflow status. Type: update case status to pending 2025P1234 and hit Send";
            default -> "Unknown task type";
        };
    }
    
    /**
     * Build warnings based on extracted entities and task
     */
    private List<String> buildWarnings(TaskType taskType, Map<String, String> entities) {
        List<String> warnings = new ArrayList<>();
        
        if (!entities.containsKey("case_id")) {
            warnings.add("No case ID found in query. You'll need to provide it manually.");
        }
        
        if (taskType == TaskType.UPDATE_CASE_STATUS && !entities.containsKey("status")) {
            warnings.add("No target status found in query. You'll need to provide it manually.");
        }
        
        if (taskType == TaskType.CANCEL_CASE) {
            warnings.add("Case cancellation is a critical operation. Please review pre-checks carefully.");
        }
        
        return warnings;
    }
}

