package com.lca.productionsupport.service;

import com.lca.productionsupport.config.WebClientRegistry;
import com.lca.productionsupport.model.OperationalResponse.RunbookStep;
import com.lca.productionsupport.model.OperationalResponse.StepGroups;
import com.lca.productionsupport.model.StepExecutionRequest;
import com.lca.productionsupport.model.StepExecutionResponse;
import com.lca.productionsupport.model.StepMethod;
import com.lca.productionsupport.model.UseCaseDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service to execute runbook steps by making actual API calls
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StepExecutionService {

    private final WebClientRegistry webClientRegistry;
    private final RunbookRegistry runbookRegistry;
    private final RunbookAdapter runbookAdapter;
    private final ErrorMessageTranslator errorMessageTranslator;
    
    /**
     * Execute a specific step
     */
    public StepExecutionResponse executeStep(StepExecutionRequest request) {
        long startTime = System.currentTimeMillis();
        
        // Get the step definition from YAML runbook
        RunbookStep step = getStepFromRunbook(request.getTaskId(), request.getStepNumber(), request.getEntities());
        
        if (step == null) {
            log.warn("Step not found for taskId: {}, stepNumber: {}", request.getTaskId(), request.getStepNumber());
            ErrorMessageTranslator.TranslationResult translation = errorMessageTranslator.translate("Step not found");
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .errorMessage(translation.getUserFriendlyMessage())
                .responseBody(translation.getTechnicalDetails())
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        }
        
        log.info("Retrieved step {}: method={}, stepType={}, description={}", 
            step.getStepNumber(), step.getMethod(), step.getStepType(), step.getDescription());
        
        StepMethod method = step.getMethod();
        
        // Check if this is a local execution step (no downstream service needed)
        if (method != null && method.isLocalExecution()) {
            log.info("Executing local step: {}", method);
            if (method == StepMethod.LOCAL_MESSAGE) {
                return executeLocalMessage(request, step, startTime);
            } else if (method == StepMethod.HEADER_CHECK) {
                return executeHeaderCheck(request, step, startTime);
            }
        }
        
        // Get the WebClient for the downstream service
        WebClient webClient;
        try {
            webClient = webClientRegistry.getWebClient(request.getDownstreamService());
        } catch (IllegalArgumentException e) {
            ErrorMessageTranslator.TranslationResult translation = errorMessageTranslator.translate(
                "Downstream service not configured: " + request.getDownstreamService()
            );
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .errorMessage(translation.getUserFriendlyMessage())
                .responseBody(translation.getTechnicalDetails())
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        }
        
        log.info("Executing step {} for service {}: {} {}", 
                request.getStepNumber(), request.getDownstreamService(), 
                step.getMethod(), step.getPath());
        
        // Replace placeholders in path and body
        String resolvedPath = resolvePlaceholders(step.getPath(), request.getEntities());
        String resolvedBody = step.getRequestBody() != null ? 
            resolvePlaceholders(step.getRequestBody(), request.getEntities()) : null;
        
        // Merge headers: YAML headers (with placeholders resolved) + request headers (request takes precedence)
        Map<String, String> mergedHeaders = mergeHeaders(step.getHeaders(), request);
        
        try {
            // Get timeout for this service
            Duration timeout = webClientRegistry.getTimeout(request.getDownstreamService());
            
            // Build and execute the request
            String responseBody = executeHttpRequest(
                webClient,
                method,
                resolvedPath,
                resolvedBody,
                request.getAuthToken(),
                request.getUserId(),
                mergedHeaders,
                timeout
            );
            
            long duration = System.currentTimeMillis() - startTime;
            
            return StepExecutionResponse.builder()
                .success(true)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .statusCode(200)
                .responseBody(responseBody)
                .durationMs(duration)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to execute step {}", request.getStepNumber(), e);
            
            // Translate technical error to user-friendly message
            ErrorMessageTranslator.TranslationResult translation = errorMessageTranslator.translate(e.getMessage());
            
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .errorMessage(translation.getUserFriendlyMessage())
                .responseBody(translation.getTechnicalDetails())
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }
    
    /**
     * Execute a local message step - returns a predefined message without any external calls
     */
    private StepExecutionResponse executeLocalMessage(StepExecutionRequest request, RunbookStep step, long startTime) {
        String message = step.getRequestBody();  // Message stored in requestBody field
        
        log.info("Executing local message step: {}", message);
        
        long duration = System.currentTimeMillis() - startTime;
        
        return StepExecutionResponse.builder()
            .success(true)
            .stepNumber(request.getStepNumber())
            .stepDescription(step.getDescription())
            .statusCode(200)
            .responseBody("{\"message\": \"" + message + "\"}")
            .durationMs(duration)
            .build();
    }
    
    /**
     * Execute a header validation check without making downstream API call
     */
    private StepExecutionResponse executeHeaderCheck(StepExecutionRequest request, RunbookStep step, long startTime) {
        String headerName = step.getPath();  // Header name stored in path field
        String expectedValue = step.getExpectedResponse();  // Expected value stored in expectedResponse field
        String actualValue = request.getUserRole();  // Get the actual role from request
        
        log.info("Executing header check: header={}, expected={}, actual={}", 
                headerName, expectedValue, actualValue);
        
        // Check if the actual value matches the expected value
        boolean isValid = actualValue != null && actualValue.equals(expectedValue);
        
        long duration = System.currentTimeMillis() - startTime;
        
        if (isValid) {
            return StepExecutionResponse.builder()
                .success(true)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .statusCode(200)
                .responseBody("{\"valid\": true, \"message\": \"User has required role: " + expectedValue + "\"}")
                .durationMs(duration)
                .build();
        } else {
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .statusCode(403)
                .errorMessage("Access denied: User role '" + actualValue + "' does not match required role '" + expectedValue + "'")
                .durationMs(duration)
                .build();
        }
    }
    
    /**
     * Execute HTTP request based on method
     */
    private String executeHttpRequest(WebClient webClient, StepMethod method, String path, 
                                     String body, String authToken, String userId, 
                                     Map<String, String> customHeaders, Duration timeout) {
        
        WebClient.RequestHeadersSpec<?> request;
        
        switch (method) {
            case GET:
                request = webClient.get().uri(path);
                break;
                
            case POST:
                request = webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body != null ? body : "{}");
                break;
                
            case PATCH:
                request = webClient.patch()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body != null ? body : "{}");
                break;
                
            case PUT:
                request = webClient.put()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body != null ? body : "{}");
                break;
                
            case DELETE:
                request = webClient.delete().uri(path);
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        
        // Add standard headers only if not already present in customHeaders
        // This allows YAML headers to override defaults
        if (customHeaders == null || !customHeaders.containsKey("Authorization")) {
            String authHeaderValue = authToken != null ? authToken : "dummy-token";
            // Add "Bearer " prefix if not already present
            if (!authHeaderValue.startsWith("Bearer ")) {
                authHeaderValue = "Bearer " + authHeaderValue;
            }
            request = request.header("Authorization", authHeaderValue);
        }
        
        if (customHeaders == null || !customHeaders.containsKey("X-User-ID")) {
            request = request.header("X-User-ID", userId != null ? userId : "system");
        }
        
        if (customHeaders == null || !customHeaders.containsKey("X-Idempotency-Key")) {
            request = request.header("X-Idempotency-Key", UUID.randomUUID().toString());
        }
        
        // Add all custom headers (from YAML or request, already merged)
        if (customHeaders != null) {
            for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                request = request.header(header.getKey(), header.getValue());
            }
        }
        
        // Execute and get response
        return request.retrieve()
            .onStatus(
                status -> status.isError(),
                response -> response.bodyToMono(String.class)
                    .flatMap(errorBody -> Mono.error(new RuntimeException("API Error: " + errorBody)))
            )
            .bodyToMono(String.class)
            .timeout(timeout)
            .block();
    }
    
    /**
     * Replace placeholders in string with actual values
     * Supports: {case_id}, {status}, {user_id}, etc.
     */
    private String resolvePlaceholders(String template, java.util.Map<String, String> entities) {
        if (template == null || entities == null) {
            return template;
        }
        
        String resolved = template;
        for (java.util.Map.Entry<String, String> entry : entities.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            resolved = resolved.replace(placeholder, entry.getValue());
        }
        
        return resolved;
    }
    
    /**
     * Merge YAML headers with request headers, resolving placeholders from request context
     * Request headers take precedence over YAML headers
     */
    private Map<String, String> mergeHeaders(Map<String, String> yamlHeaders, StepExecutionRequest request) {
        Map<String, String> merged = new HashMap<>();
        
        // First, add YAML headers with placeholders resolved from request context
        if (yamlHeaders != null && !yamlHeaders.isEmpty()) {
            for (Map.Entry<String, String> entry : yamlHeaders.entrySet()) {
                String headerValue = resolveHeaderPlaceholders(entry.getValue(), request);
                merged.put(entry.getKey(), headerValue);
            }
        }
        
        // Then, add/override with headers from request (request headers take precedence)
        if (request.getCustomHeaders() != null) {
            merged.putAll(request.getCustomHeaders());
        }
        
        return merged;
    }
    
    /**
     * Resolve placeholders in header values from request context
     * Supports: {api_user}, {lab_id}, {discipline_name}, {time_zone}, {role_name}, {token}, {user_id}, {IDEMPOTENCY_KEY}
     */
    private String resolveHeaderPlaceholders(String template, StepExecutionRequest request) {
        if (template == null) {
            return template;
        }
        
        String resolved = template;
        Map<String, String> customHeaders = request.getCustomHeaders();
        
        // Resolve from custom headers (request headers)
        if (customHeaders != null) {
            resolved = resolved.replace("{api_user}", customHeaders.getOrDefault("Api-User", "{api_user}"));
            resolved = resolved.replace("{lab_id}", customHeaders.getOrDefault("Lab-Id", "{lab_id}"));
            resolved = resolved.replace("{discipline_name}", customHeaders.getOrDefault("Discipline-Name", "{discipline_name}"));
            resolved = resolved.replace("{time_zone}", customHeaders.getOrDefault("Time-Zone", "{time_zone}"));
            resolved = resolved.replace("{role_name}", customHeaders.getOrDefault("Role-Name", "{role_name}"));
        }
        
        // Resolve from request body fields
        if (request.getAuthToken() != null) {
            // Remove "Bearer " prefix if present
            String token = request.getAuthToken().startsWith("Bearer ") 
                ? request.getAuthToken().substring(7) 
                : request.getAuthToken();
            resolved = resolved.replace("{token}", token);
        }
        
        if (request.getUserId() != null) {
            resolved = resolved.replace("{user_id}", request.getUserId());
        }
        
        // Generate idempotency key if needed
        if (resolved.contains("{IDEMPOTENCY_KEY}")) {
            resolved = resolved.replace("{IDEMPOTENCY_KEY}", UUID.randomUUID().toString());
        }
        
        return resolved;
    }
    
    /**
     * Get a specific step from YAML runbook
     */
    private RunbookStep getStepFromRunbook(String taskId, Integer stepNumber, Map<String, String> entities) {
        // Handle null taskId
        if (taskId == null) {
            log.warn("TaskId is null, cannot retrieve step");
            return null;
        }
        
        // Get use case definition
        UseCaseDefinition useCase = runbookRegistry.getUseCase(taskId);
        if (useCase == null) {
            log.warn("No runbook found for taskId: {}", taskId);
            return null;
        }
        
        // Convert to operational response to get steps
        Map<String, String> safeEntities = entities != null ? entities : new HashMap<>();
        var response = runbookAdapter.toOperationalResponse(useCase, safeEntities);
        StepGroups stepGroups = response.getSteps();
        
        // Find the step by number
        List<RunbookStep> allSteps = new ArrayList<>();
        if (stepGroups.getPrechecks() != null) allSteps.addAll(stepGroups.getPrechecks());
        if (stepGroups.getProcedure() != null) allSteps.addAll(stepGroups.getProcedure());
        if (stepGroups.getPostchecks() != null) allSteps.addAll(stepGroups.getPostchecks());
        if (stepGroups.getRollback() != null) allSteps.addAll(stepGroups.getRollback());
        
        return allSteps.stream()
            .filter(s -> s.getStepNumber().equals(stepNumber))
            .findFirst()
            .orElse(null);
    }
}

