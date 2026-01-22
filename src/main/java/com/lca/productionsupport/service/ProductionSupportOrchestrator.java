package com.lca.productionsupport.service;

import com.lca.productionsupport.model.UseCaseDefinition;
import com.lca.productionsupport.model.OperationalRequest;
import com.lca.productionsupport.model.OperationalResponse;
import com.lca.productionsupport.model.TaskInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main orchestrator that coordinates classification and runbook retrieval
 * Purely YAML-driven - no hardcoded logic
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionSupportOrchestrator {

    private final RunbookRegistry runbookRegistry;
    private final RunbookClassifier runbookClassifier;
    private final RunbookEntityExtractor entityExtractor;
    private final RunbookAdapter runbookAdapter;
    
    /**
     * Process an operational request and return next steps
     */
    public OperationalResponse processRequest(OperationalRequest request) {
        log.info("Processing request: {} for downstream service: {}", 
                request.getQuery(), request.getDownstreamService());
        
        // Step 1: Classify the request (or use explicit taskId if provided)
        String taskId;
        UseCaseDefinition useCase;
        
        if (request.getTaskId() != null && !request.getTaskId().isEmpty()) {
            // Explicit task ID provided
            taskId = request.getTaskId();
            useCase = runbookRegistry.getUseCase(taskId);
            
            if (useCase == null) {
                log.warn("No runbook found for explicit taskId: {}", taskId);
                return buildUnknownResponse(request);
            }
        } else {
            // Classify using runbook classifier
            taskId = runbookClassifier.classify(request.getQuery());
            
            if ("UNKNOWN".equals(taskId)) {
                log.warn("Could not classify request: {}", request.getQuery());
                return buildUnknownResponse(request);
            }
            
            useCase = runbookRegistry.getUseCase(taskId);
            
            if (useCase == null) {
                log.warn("Classifier returned {}, but no runbook found", taskId);
                return buildUnknownResponse(request);
            }
        }
        
        // Step 2: Extract entities
        Map<String, String> entities = entityExtractor.extract(
            request.getQuery(),
            useCase.getExtraction()
        );
        
        // Step 3: Validate required entities
        if (!validateRequiredEntities(useCase, entities)) {
            log.warn("Required entities not found for use case: {}", taskId);
            // Still return response with warnings
        }
        
        // Step 4: Convert to OperationalResponse
        OperationalResponse response = runbookAdapter.toOperationalResponse(useCase, entities);
        
        // Override downstream service if specified in request
        if (request.getDownstreamService() != null && !request.getDownstreamService().isEmpty()) {
            response.setDownstreamService(request.getDownstreamService());
        }
        
        return response;
    }
    
    /**
     * Validate that all required entities are present
     */
    private boolean validateRequiredEntities(UseCaseDefinition useCase, Map<String, String> entities) {
        if (useCase.getClassification() == null || 
            useCase.getClassification().getRequiredEntities() == null) {
            return true;
        }
        
        for (String requiredEntity : useCase.getClassification().getRequiredEntities()) {
            if (!entities.containsKey(requiredEntity) || entities.get(requiredEntity) == null) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Build response for unknown/unclassified requests
     */
    private OperationalResponse buildUnknownResponse(OperationalRequest request) {
        return OperationalResponse.builder()
            .taskId("UNKNOWN")
            .taskName("Unknown")
            .downstreamService(request.getDownstreamService())
            .extractedEntities(Map.of())
            .steps(OperationalResponse.StepGroups.builder().build())
            .warnings(List.of("Unable to classify the request. Please try rephrasing or select a task manually."))
            .build();
    }
    
    /**
     * Get all available task types
     * Useful for UI to display options when pattern detection fails
     */
    public List<TaskInfo> getAvailableTasks() {
        List<TaskInfo> tasks = new ArrayList<>();
        
        // Add all runbooks from registry
        for (UseCaseDefinition useCase : runbookRegistry.getAllUseCases()) {
            TaskInfo.TaskInfoBuilder taskBuilder = TaskInfo.builder()
                .taskId(useCase.getUseCase().getId())
                .taskName(useCase.getUseCase().getName())
                .description(useCase.getUseCase().getDescription() != null ? 
                    useCase.getUseCase().getDescription() : "");
            
            // Add example query if available
            if (useCase.getUseCase().getExampleQuery() != null && 
                !useCase.getUseCase().getExampleQuery().isEmpty()) {
                taskBuilder.exampleQuery(useCase.getUseCase().getExampleQuery());
            }
            
            // Extract validInputs from entity enumValues
            List<TaskInfo.ValidInput> validInputs = extractValidInputs(useCase);
            if (!validInputs.isEmpty()) {
                taskBuilder.validInputs(validInputs);
            }
            
            tasks.add(taskBuilder.build());
        }
        
        return tasks;
    }
    
    /**
     * Extract validInputs from entity validations that have enumValues
     */
    private List<TaskInfo.ValidInput> extractValidInputs(UseCaseDefinition useCase) {
        List<TaskInfo.ValidInput> validInputs = new ArrayList<>();
        
        if (useCase.getExtraction() == null || useCase.getExtraction().getEntities() == null) {
            return validInputs;
        }
        
        for (Map.Entry<String, UseCaseDefinition.EntityConfig> entry : useCase.getExtraction().getEntities().entrySet()) {
            String entityName = entry.getKey();
            UseCaseDefinition.EntityConfig entityConfig = entry.getValue();
            
            if (entityConfig.getValidation() != null && 
                entityConfig.getValidation().getEnumValues() != null && 
                !entityConfig.getValidation().getEnumValues().isEmpty()) {
                
                // Create a user-friendly name for the valid inputs
                String displayName = formatEntityNameForDisplay(entityName);
                TaskInfo.ValidInput validInput = TaskInfo.ValidInput.builder()
                    .name(displayName)
                    .list(entityConfig.getValidation().getEnumValues())
                    .build();
                validInputs.add(validInput);
            }
        }
        
        return validInputs;
    }
    
    /**
     * Format entity name for display (e.g., "sampleStatus" -> "Valid Sample statuses")
     */
    private String formatEntityNameForDisplay(String entityName) {
        // Convert camelCase to Title Case with spaces
        String formatted = entityName.replaceAll("([a-z])([A-Z])", "$1 $2");
        formatted = formatted.substring(0, 1).toUpperCase() + formatted.substring(1);
        
        // Add "Valid" prefix and pluralize if needed
        // Handle common pluralization rules
        if (formatted.endsWith("s") || formatted.endsWith("x") || formatted.endsWith("z") || 
            formatted.endsWith("ch") || formatted.endsWith("sh")) {
            return "Valid " + formatted + "es";
        } else if (formatted.endsWith("y") && !formatted.matches(".*[aeiou]y$")) {
            return "Valid " + formatted.substring(0, formatted.length() - 1) + "ies";
        } else if (!formatted.endsWith("s")) {
            return "Valid " + formatted + "s";
        } else {
            return "Valid " + formatted;
        }
    }
}
