package com.lca.productionsupport.service;

import com.lca.productionsupport.model.UseCaseDefinition;
import com.lca.productionsupport.model.UseCaseDefinition.*;
import com.lca.productionsupport.model.OperationalResponse;
import com.lca.productionsupport.model.OperationalResponse.*;
import com.lca.productionsupport.model.StepMethod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapts YAML runbook definitions to OperationalResponse format
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunbookAdapter {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Convert YAML runbook definition to OperationalResponse
     */
    public OperationalResponse toOperationalResponse(
            UseCaseDefinition useCase,
            Map<String, String> extractedEntities) {
        
        // Group steps by type
        StepGroups stepGroups = groupSteps(useCase.getExecution().getSteps(), extractedEntities);
        
        return OperationalResponse.builder()
                .taskId(useCase.getUseCase().getId())
                .taskName(useCase.getUseCase().getName())
                .downstreamService(getDownstreamService(useCase))
                .extractedEntities(extractedEntities)
                .steps(stepGroups)
                .warnings(useCase.getWarnings())
                .build();
    }
    
    private String getDownstreamService(UseCaseDefinition useCase) {
        if (useCase.getUseCase().getDownstreamService() != null) {
            return useCase.getUseCase().getDownstreamService();
        }
        return "ap-services"; // Default
    }
    
    private StepGroups groupSteps(List<StepDefinition> steps, Map<String, String> entities) {
        List<RunbookStep> prechecks = new ArrayList<>();
        List<RunbookStep> procedure = new ArrayList<>();
        List<RunbookStep> postchecks = new ArrayList<>();
        List<RunbookStep> rollback = new ArrayList<>();
        
        for (StepDefinition step : steps) {
            RunbookStep runbookStep = convertStep(step, entities);
            
            String stepType = step.getStepType();
            if (stepType == null) {
                stepType = "procedure"; // Default
            }
            
            switch (stepType.toLowerCase()) {
                case "prechecks", "precheck" -> prechecks.add(runbookStep);
                case "procedure" -> procedure.add(runbookStep);
                case "postchecks", "postcheck" -> postchecks.add(runbookStep);
                case "rollback" -> rollback.add(runbookStep);
                default -> procedure.add(runbookStep); // Default to procedure
            }
        }
        
        return StepGroups.builder()
                .prechecks(prechecks)
                .procedure(procedure)
                .postchecks(postchecks)
                .rollback(rollback)
                .build();
    }
    
    private RunbookStep convertStep(StepDefinition step, Map<String, String> entities) {
        // Replace placeholders in path and body
        String path = replacePlaceholders(step.getPath(), entities);
        String requestBody = formatRequestBody(step.getBody(), entities);
        String description = replacePlaceholders(step.getDescription(), entities);
        String expectedResponse = replacePlaceholders(step.getExpectedResponse(), entities);
        
        // Handle LOCAL_MESSAGE step type
        if ("LOCAL_MESSAGE".equalsIgnoreCase(step.getMethod())) {
            String message = step.getLocalMessage();
            if (message == null && step.getDescription() != null) {
                message = step.getDescription();
            }
            message = replacePlaceholders(message, entities);
            
            return RunbookStep.builder()
                    .stepNumber(step.getStepNumber())
                    .name(step.getName())
                    .description(message)
                    .method(StepMethod.LOCAL_MESSAGE)
                    .path(null)
                    .requestBody(message)
                    .expectedResponse(message)
                    .autoExecutable(true)
                    .stepType(step.getStepType())
                    .build();
        }
        
        // Handle HEADER_CHECK step type
        if ("HEADER_CHECK".equalsIgnoreCase(step.getMethod())) {
            String headerName = step.getPath(); // Path contains the header name
            String expectedValue = replacePlaceholders(step.getExpectedResponse(), entities);
            
            return RunbookStep.builder()
                    .stepNumber(step.getStepNumber())
                    .name(step.getName())
                    .description(description != null ? description : "Verify " + headerName + " header")
                    .method(StepMethod.HEADER_CHECK)
                    .path(headerName)
                    .requestBody(null)
                    .expectedResponse(expectedValue)
                    .autoExecutable(true)
                    .stepType(step.getStepType())
                    .build();
        }
        
        // Handle regular HTTP method steps
        // Keep headers from YAML as-is (placeholders will be resolved later from request context)
        // Only resolve entity placeholders (like {case_id}) but keep request context placeholders (like {api_user})
        Map<String, String> processedHeaders = null;
        if (step.getHeaders() != null && !step.getHeaders().isEmpty()) {
            processedHeaders = new java.util.HashMap<>();
            for (Map.Entry<String, String> headerEntry : step.getHeaders().entrySet()) {
                // Only resolve entity placeholders here, request context placeholders resolved later
                String headerValue = replacePlaceholders(headerEntry.getValue(), entities);
                processedHeaders.put(headerEntry.getKey(), headerValue);
            }
        }
        
        return RunbookStep.builder()
                .stepNumber(step.getStepNumber())
                .name(step.getName())
                .description(description)
                .method(StepMethod.fromString(step.getMethod()))
                .path(path)
                .requestBody(requestBody)
                .expectedResponse(expectedResponse)
                .autoExecutable(step.isAutoExecutable())
                .stepType(step.getStepType())
                .headers(processedHeaders)
                .build();
    }
    
    private String replacePlaceholders(String template, Map<String, String> entities) {
        if (template == null || entities == null) {
            return template;
        }
        
        String result = template;
        for (Map.Entry<String, String> entry : entities.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
    
    private String formatRequestBody(Map<String, Object> body, Map<String, String> entities) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        
        try {
            // Replace placeholders in body values
            Map<String, Object> replacedBody = replacePlaceholdersInMap(body, entities);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(replacedBody);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request body", e);
            return body.toString();
        }
    }
    
    private Map<String, Object> replacePlaceholdersInMap(Map<String, Object> map, Map<String, String> entities) {
        Map<String, Object> result = new java.util.HashMap<>(map);
        
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            Object value = entry.getValue();
            
            if (value instanceof String) {
                entry.setValue(replacePlaceholders((String) value, entities));
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                entry.setValue(replacePlaceholdersInMap(nestedMap, entities));
            }
        }
        
        return result;
    }
}

