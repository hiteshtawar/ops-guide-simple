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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper = new ObjectMapper();
    
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
            } else if (method == StepMethod.ENTITY_VALIDATION) {
                return executeEntityValidation(request, step, startTime);
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
        
        try {
            // Replace placeholders in path and body
            String resolvedPath = resolvePlaceholders(step.getPath(), request.getEntities());
            String resolvedBody = step.getRequestBody() != null ? 
                resolvePlaceholders(step.getRequestBody(), request.getEntities()) : null;
            
            // Merge headers: YAML headers (with placeholders resolved) + request headers (request takes precedence)
            Map<String, String> mergedHeaders = mergeHeaders(step.getHeaders(), request);
            
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
            
            // Verify response and generate stepResponse if verification config exists
            String stepResponse = null;
            if (step.getVerificationExpectedFields() != null || step.getVerificationRequiredFields() != null) {
                stepResponse = verifyAndGenerateStepResponse(responseBody, step, request.getEntities(), request);
            } else if (step.getStepResponseMessage() != null) {
                // Generate stepResponse from template if no verification but template exists
                stepResponse = replacePlaceholdersInMessage(step.getStepResponseMessage(), Map.of(), request.getEntities());
                // Also resolve header placeholders like {api_user}
                stepResponse = resolveHeaderPlaceholders(stepResponse, request);
            }
            
            return StepExecutionResponse.builder()
                .success(true)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .statusCode(200)
                .responseBody(responseBody)
                .stepResponse(stepResponse)
                .durationMs(duration)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to execute step {}", request.getStepNumber(), e);
            
            // Translate technical error to user-friendly message
            ErrorMessageTranslator.TranslationResult translation = errorMessageTranslator.translate(e.getMessage());
            
            // Extract API error message from responseBody if available
            String apiErrorMessage = extractApiErrorMessage(translation.getTechnicalDetails());
            
            // Generate stepResponseErrorMessage from template if provided
            String stepResponse = null;
            if (step.getStepResponseErrorMessage() != null) {
                stepResponse = replacePlaceholdersInMessage(step.getStepResponseErrorMessage(), Map.of(), request.getEntities());
            } else {
                // Fall back to translated error message
                stepResponse = translation.getUserFriendlyMessage();
            }
            
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .errorMessage(translation.getUserFriendlyMessage())
                .responseBody(translation.getTechnicalDetails())
                .apiErrorMessage(apiErrorMessage)
                .stepResponse(stepResponse)
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }
    
    /**
     * Execute a local message step - returns a predefined message without any external calls
     */
    private StepExecutionResponse executeLocalMessage(StepExecutionRequest request, RunbookStep step, long startTime) {
        String message = step.getRequestBody();  // Message stored in requestBody field
        String stepResponseMessage = step.getStepResponseMessage();  // Optional stepResponseMessage template
        
        log.info("Executing local message step: {}", message);
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Use stepResponseMessage if provided, otherwise use the localMessage
        String stepResponse = stepResponseMessage != null
            ? replacePlaceholdersInMessage(stepResponseMessage, Map.of(), request.getEntities())
            : message;
        
        return StepExecutionResponse.builder()
            .success(true)
            .stepNumber(request.getStepNumber())
            .stepDescription(step.getDescription())
            .statusCode(200)
            .responseBody("{\"message\": \"" + message + "\"}")
            .stepResponse(stepResponse)
            .durationMs(duration)
            .build();
    }
    
    /**
     * Execute entity validation check without making downstream API call
     */
    private StepExecutionResponse executeEntityValidation(StepExecutionRequest request, RunbookStep step, long startTime) {
        String entityName = step.getPath();  // Entity name to validate stored in path field
        String stepResponseMessage = step.getStepResponseMessage();  // Success message template
        String stepResponseErrorMessage = step.getStepResponseErrorMessage();  // Error message template
        
        log.info("Executing entity validation: entity={}", entityName);
        
        // Get the use case definition to access validation rules
        UseCaseDefinition useCaseDef = runbookRegistry.getUseCase(request.getTaskId());
        if (useCaseDef == null) {
            long duration = System.currentTimeMillis() - startTime;
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .statusCode(500)
                .errorMessage("Use case definition not found for taskId: " + request.getTaskId())
                .durationMs(duration)
                .build();
        }
        
        // Get the entity config
        UseCaseDefinition.EntityConfig entityConfig = null;
        if (useCaseDef.getExtraction() != null && useCaseDef.getExtraction().getEntities() != null) {
            entityConfig = useCaseDef.getExtraction().getEntities().get(entityName);
        }
        
        if (entityConfig == null || entityConfig.getValidation() == null) {
            long duration = System.currentTimeMillis() - startTime;
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .statusCode(500)
                .errorMessage("No validation configuration found for entity: " + entityName)
                .durationMs(duration)
                .build();
        }
        
        // Get the actual value from entities
        String actualValue = request.getEntities() != null ? request.getEntities().get(entityName) : null;
        if (actualValue == null) {
            long duration = System.currentTimeMillis() - startTime;
            
            // Build error message with allowed values if available
            UseCaseDefinition.ValidationConfig validation = entityConfig.getValidation();
            String errorMessage;
            if (stepResponseErrorMessage != null) {
                // Use stepResponseErrorMessage template, but entity is missing
                // Replace {entityName} placeholder with entity name + " not provided" to keep entity name in message
                errorMessage = stepResponseErrorMessage.replace("{" + entityName + "}", entityName + " not provided");
                // Also replace any remaining placeholders from request entities
                if (errorMessage != null && request.getEntities() != null) {
                    String replaced = replacePlaceholdersInMessage(errorMessage, Map.of(), request.getEntities());
                    if (replaced != null) {
                        errorMessage = replaced;
                    }
                }
                // Append allowed values if enumValues exist
                if (errorMessage != null && validation != null && validation.getEnumValues() != null && !validation.getEnumValues().isEmpty()) {
                    String allowedValuesList = String.join(", ", validation.getEnumValues());
                    if (!errorMessage.contains("Allowed values") && !errorMessage.contains("allowed list")) {
                        errorMessage += " Allowed values: " + allowedValuesList;
                    }
                }
                // Ensure errorMessage is not null after all processing
                if (errorMessage == null) {
                    errorMessage = "Required entity '" + entityName + "' not provided";
                }
            } else {
                errorMessage = "Required entity '" + entityName + "' not provided";
                // Append allowed values if enumValues exist
                if (validation != null && validation.getEnumValues() != null && !validation.getEnumValues().isEmpty()) {
                    errorMessage += ". Allowed values: " + String.join(", ", validation.getEnumValues());
                }
            }
            
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .statusCode(400)
                .errorMessage(errorMessage)
                .stepResponse(errorMessage)
                .durationMs(duration)
                .build();
        }
        
        // Validate the entity value
        UseCaseDefinition.ValidationConfig validation = entityConfig.getValidation();
        boolean isValid = true;
        String validationError = null;
        
        // Check enum values
        if (validation.getEnumValues() != null && !validation.getEnumValues().isEmpty()) {
            boolean matchesEnum = validation.getEnumValues().stream()
                .anyMatch(enumVal -> enumVal.equalsIgnoreCase(actualValue));
            
            if (!matchesEnum) {
                isValid = false;
                validationError = validation.getErrorMessage() != null 
                    ? validation.getErrorMessage() 
                    : "Invalid " + entityName + " provided: '" + actualValue + "'. Allowed values: " + validation.getEnumValues();
            }
        }
        
        // Check regex if defined
        if (isValid && validation.getRegex() != null) {
            if (!actualValue.matches(validation.getRegex())) {
                isValid = false;
                validationError = validation.getErrorMessage() != null 
                    ? validation.getErrorMessage() 
                    : "Value '" + actualValue + "' does not match required pattern";
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        if (isValid) {
            // Generate success message from template
            String successMessage = stepResponseMessage != null 
                ? replacePlaceholdersInMessage(stepResponseMessage, Map.of(entityName, actualValue), request.getEntities())
                : entityName + " '" + actualValue + "' is valid";
            
            return StepExecutionResponse.builder()
                .success(true)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .statusCode(200)
                .responseBody("{\"valid\": true, \"" + entityName + "\": \"" + actualValue + "\"}")
                .stepResponse(successMessage)
                .durationMs(duration)
                .build();
        } else {
            // Generate error message from template
            String errorMessage = stepResponseErrorMessage != null 
                ? replacePlaceholdersInMessage(stepResponseErrorMessage, Map.of(entityName, actualValue), request.getEntities())
                : validationError;
            
            // Append allowed values list if enumValues exist and not already in message
            if (validation.getEnumValues() != null && !validation.getEnumValues().isEmpty()) {
                String allowedValuesList = String.join(", ", validation.getEnumValues());
                if (!errorMessage.contains("Allowed values") && !errorMessage.contains("allowed list")) {
                    errorMessage += " Allowed values: " + allowedValuesList;
                }
            }
            
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .statusCode(400)
                .errorMessage(errorMessage)
                .stepResponse(errorMessage)
                .durationMs(duration)
                .build();
        }
    }
    
    /**
     * Execute a header validation check without making downstream API call
     */
    private StepExecutionResponse executeHeaderCheck(StepExecutionRequest request, RunbookStep step, long startTime) {
        String headerName = step.getPath();  // Header name stored in path field
        String expectedValue = step.getExpectedResponse();  // Expected value stored in expectedResponse field
        String actualValue = request.getUserRole();  // Get the actual role from request
        String stepResponseMessage = step.getStepResponseMessage();  // Success message template
        String stepResponseErrorMessage = step.getStepResponseErrorMessage();  // Error message template
        
        log.info("Executing header check: header={}, expected={}, actual={}", 
                headerName, expectedValue, actualValue);
        
        // Check if the actual value matches the expected value
        boolean isValid = actualValue != null && actualValue.equals(expectedValue);
        
        long duration = System.currentTimeMillis() - startTime;
        
        if (isValid) {
            // Generate success message from template or use default
            String successMessage = stepResponseMessage != null 
                ? replacePlaceholdersInMessage(stepResponseMessage, Map.of("role", expectedValue), request.getEntities())
                : "User has required role: " + expectedValue;
            
            return StepExecutionResponse.builder()
                .success(true)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .statusCode(200)
                .responseBody("{\"valid\": true, \"message\": \"" + successMessage + "\"}")
                .stepResponse(successMessage)
                .durationMs(duration)
                .build();
        } else {
            // Generate error message from template or use default
            String errorMessage = stepResponseErrorMessage != null
                ? replacePlaceholdersInMessage(stepResponseErrorMessage, Map.of("role", expectedValue, "actualRole", actualValue != null ? actualValue : "null"), request.getEntities())
                : "Access denied: User role '" + actualValue + "' does not match required role '" + expectedValue + "'";
            
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .statusCode(403)
                .errorMessage(errorMessage)
                .stepResponse(errorMessage)
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
                // Some GET endpoints require a request body (e.g., getWorkpoolEntry)
                if (body != null && !body.isEmpty()) {
                    // Use method() to support GET with body
                    request = webClient.method(HttpMethod.GET)
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(body));
                } else {
                    request = webClient.get().uri(path);
                }
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
                // Some DELETE endpoints require a request body (e.g., deleteWorkpoolEntry)
                if (body != null && !body.isEmpty()) {
                    // Use method() to support DELETE with body
                    request = webClient.method(HttpMethod.DELETE)
                        .uri(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(body));
                } else {
                    request = webClient.delete().uri(path);
                }
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        
        // Add Content-Type header for GET requests with body
        if (method == StepMethod.GET && body != null && !body.isEmpty()) {
            if (customHeaders == null || !customHeaders.containsKey("Content-Type")) {
                request = request.header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            }
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
     * Throws IllegalArgumentException if any variable-like placeholders remain unresolved.
     * Note: Only validates placeholders that look like variable names (alphanumeric with underscores/hyphens),
     * not JSON structure braces.
     */
    private String resolvePlaceholders(String template, java.util.Map<String, String> entities) {
        if (template == null) {
            return template;
        }
        
        if (entities == null || entities.isEmpty()) {
            // Check if template has variable-like placeholders (not JSON braces)
            java.util.regex.Pattern varPattern = java.util.regex.Pattern.compile("\\{([A-Za-z0-9_\\-]+)\\}");
            java.util.regex.Matcher matcher = varPattern.matcher(template);
            if (matcher.find()) {
                String missingVar = matcher.group(1);
                throw new IllegalArgumentException("Not enough variable values available to expand '" + missingVar + "'");
            }
            return template;
        }
        
        String resolved = template;
        for (java.util.Map.Entry<String, String> entry : entities.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            resolved = resolved.replace(placeholder, entry.getValue());
        }
        
        // Check if any variable-like placeholders remain unresolved (ignore JSON structure)
        java.util.regex.Pattern varPattern = java.util.regex.Pattern.compile("\\{([A-Za-z0-9_\\-]+)\\}");
        java.util.regex.Matcher matcher = varPattern.matcher(resolved);
        if (matcher.find()) {
            String missingVar = matcher.group(1);
            throw new IllegalArgumentException("Not enough variable values available to expand '" + missingVar + "'");
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
            String apiUserValue = customHeaders.getOrDefault("Api-User", "{api_user}");
            log.debug("Resolving {api_user} placeholder - Api-User header value: {}, customHeaders keys: {}", 
                    apiUserValue, customHeaders.keySet());
            resolved = resolved.replace("{api_user}", apiUserValue);
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
    
    /**
     * Extract the message field from API error responseBody
     * The API response is JSON with a "message" field
     * Handles format: "API Error: {\"status\":404,\"message\":\"Accession Case not found\"}"
     * or direct JSON: {"status":404,"message":"Accession Case not found"}
     * Returns the message value, or null if not found
     */
    String extractApiErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }
        
        try {
            String jsonPart = responseBody;
            
            // Remove "API Error: " prefix if present (we add this prefix in executeHttpRequest)
            if (responseBody.startsWith("API Error: ")) {
                jsonPart = responseBody.substring("API Error: ".length()).trim();
            }
            
            // Parse JSON and extract message field (this is the actual API response)
            JsonNode jsonNode = objectMapper.readTree(jsonPart);
            if (jsonNode.has("message") && jsonNode.get("message").isTextual()) {
                return jsonNode.get("message").asText();
            }
        } catch (Exception e) {
            // If parsing fails, return null (keep it simple)
            log.debug("Could not extract API error message from responseBody: {}", responseBody, e);
        }
        
        return null;
    }
    
    /**
     * Verify API response against expected fields and generate stepResponse message
     * Returns the generated message if verification passes, null otherwise
     */
    String verifyAndGenerateStepResponse(String responseBody, RunbookStep step, Map<String, String> entities, StepExecutionRequest request) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }
        
        try {
            // Try to parse as JSON first
            JsonNode jsonNode = null;
            boolean isPlainString = false;
            String plainStringValue = null;
            
            try {
                jsonNode = objectMapper.readTree(responseBody);
                // Check if it's a plain string (not an object or array)
                if (jsonNode.isTextual()) {
                    isPlainString = true;
                    plainStringValue = jsonNode.asText();
                }
            } catch (Exception e) {
                // If parsing fails, check if it looks like a simple string value
                // (not JSON object/array syntax)
                String trimmed = responseBody.trim();
                // If it doesn't start with { or [ and is not empty, treat as plain string
                if (!trimmed.startsWith("{") && !trimmed.startsWith("[") && !trimmed.isEmpty()) {
                    isPlainString = true;
                    plainStringValue = trimmed;
                    // Remove surrounding quotes if present
                    if (plainStringValue.startsWith("\"") && plainStringValue.endsWith("\"")) {
                        plainStringValue = plainStringValue.substring(1, plainStringValue.length() - 1);
                    }
                } else {
                    // Invalid JSON that looks like it should be JSON, return null
                    log.debug("Response body appears to be invalid JSON: {}", responseBody);
                    return null;
                }
            }
            
            // Handle plain string response
            if (isPlainString && plainStringValue != null) {
                // For plain string, if we have expectedFields, use the first expected field
                if (step.getVerificationExpectedFields() != null && !step.getVerificationExpectedFields().isEmpty()) {
                    Map.Entry<String, String> firstExpected = step.getVerificationExpectedFields().entrySet().iterator().next();
                    String fieldName = firstExpected.getKey();
                    String expectedValue = firstExpected.getValue();
                    
                    // Compare plain string value with expected value (case-insensitive)
                    if (!plainStringValue.equalsIgnoreCase(expectedValue)) {
                        log.warn("Plain string mismatch: expected '{}', got '{}'", expectedValue, plainStringValue);
                        // Generate error message if template exists
                        if (step.getStepResponseErrorMessage() != null) {
                            String errorMsg = step.getStepResponseErrorMessage();
                            errorMsg = errorMsg.replace("{" + fieldName + "}", plainStringValue);
                            errorMsg = errorMsg.replace("{statusString}", plainStringValue);
                            if (entities != null) {
                                for (Map.Entry<String, String> entry : entities.entrySet()) {
                                    errorMsg = errorMsg.replace("{" + entry.getKey() + "}", entry.getValue());
                                }
                            }
                            return errorMsg;
                        }
                        return null;
                    }
                }
                
                // Generate success message from template
                if (step.getStepResponseMessage() != null) {
                    String template = step.getStepResponseMessage();
                    String stepResponse = template;
                    
                    // Replace {status} or the first expected field name with the plain string value
                    if (step.getVerificationExpectedFields() != null && !step.getVerificationExpectedFields().isEmpty()) {
                        String fieldName = step.getVerificationExpectedFields().keySet().iterator().next();
                        stepResponse = stepResponse.replace("{" + fieldName + "}", plainStringValue);
                        stepResponse = stepResponse.replace("{statusString}", plainStringValue);
                    } else {
                        // Default to "status" if no expected fields defined
                        stepResponse = stepResponse.replace("{status}", plainStringValue);
                        stepResponse = stepResponse.replace("{statusString}", plainStringValue);
                    }
                    
                    // Replace any remaining entity placeholders
                    if (entities != null) {
                        for (Map.Entry<String, String> entry : entities.entrySet()) {
                            stepResponse = stepResponse.replace("{" + entry.getKey() + "}", entry.getValue());
                        }
                    }
                    
                    // Resolve header placeholders like {api_user}
                    if (request != null) {
                        stepResponse = resolveHeaderPlaceholders(stepResponse, request);
                    }
                    
                    return stepResponse;
                }
                
                return null;
            }
            
            // Handle JSON object response (original logic)
            if (jsonNode != null && jsonNode.isObject()) {
                // Verify required fields are present
                if (step.getVerificationRequiredFields() != null) {
                    for (String requiredField : step.getVerificationRequiredFields()) {
                        if (!jsonNode.has(requiredField)) {
                            log.warn("Required field '{}' not found in response", requiredField);
                            // If error message template exists, generate error message
                            if (step.getStepResponseErrorMessage() != null) {
                                return generateErrorMessageFromTemplate(step.getStepResponseErrorMessage(), jsonNode, entities);
                            }
                            return null;
                        }
                    }
                }
                
                // Verify expected fields match
                if (step.getVerificationExpectedFields() != null) {
                    for (Map.Entry<String, String> entry : step.getVerificationExpectedFields().entrySet()) {
                        String fieldName = entry.getKey();
                        String expectedValue = entry.getValue();
                        
                        if (!jsonNode.has(fieldName)) {
                            log.warn("Expected field '{}' not found in response", fieldName);
                            // If error message template exists, generate error message
                            if (step.getStepResponseErrorMessage() != null) {
                                return generateErrorMessageFromTemplate(step.getStepResponseErrorMessage(), jsonNode, entities);
                            }
                            return null;
                        }
                        
                        String actualValue = jsonNode.get(fieldName).asText();
                        // Use case-insensitive comparison for status and other text fields
                        if (!actualValue.equalsIgnoreCase(expectedValue)) {
                            log.warn("Field '{}' mismatch: expected '{}', got '{}'", fieldName, expectedValue, actualValue);
                            // If error message template exists, generate error message with actual value
                            if (step.getStepResponseErrorMessage() != null) {
                                return generateErrorMessageFromTemplate(step.getStepResponseErrorMessage(), jsonNode, entities);
                            }
                            return null;
                        }
                    }
                }
                
                // Generate stepResponse from template using actual values from response (verification passed)
                if (step.getStepResponseMessage() != null) {
                    String template = step.getStepResponseMessage();
                    String stepResponse = template;
                    
                    // Replace placeholders with actual values from JSON response
                    // Extract all fields from JSON response for template replacement
                    java.util.Iterator<String> fieldNames = jsonNode.fieldNames();
                    while (fieldNames.hasNext()) {
                        String fieldName = fieldNames.next();
                        JsonNode fieldValue = jsonNode.get(fieldName);
                        if (fieldValue.isTextual()) {
                            String value = fieldValue.asText();
                            stepResponse = stepResponse.replace("{" + fieldName + "}", value);
                        } else if (fieldValue.isNumber() || fieldValue.isBoolean()) {
                            String value = fieldValue.asText();
                            stepResponse = stepResponse.replace("{" + fieldName + "}", value);
                        }
                    }
                    
                    // Replace any remaining entity placeholders (e.g., {case_id})
                    if (entities != null) {
                        for (Map.Entry<String, String> entry : entities.entrySet()) {
                            stepResponse = stepResponse.replace("{" + entry.getKey() + "}", entry.getValue());
                        }
                    }
                    
                    // Resolve header placeholders like {api_user}
                    if (request != null) {
                        stepResponse = resolveHeaderPlaceholders(stepResponse, request);
                    }
                    
                    return stepResponse;
                }
            }
            
        } catch (Exception e) {
            log.debug("Could not verify response or generate stepResponse: {}", responseBody, e);
        }
        
        return null;
    }
    
    /**
     * Generate error message from template when verification fails
     */
    private String generateErrorMessageFromTemplate(String template, JsonNode jsonNode, Map<String, String> entities) {
        String errorMessage = template;
        
        // Replace placeholders with actual values from JSON response
        java.util.Iterator<String> fieldNames = jsonNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode fieldValue = jsonNode.get(fieldName);
            if (fieldValue.isTextual()) {
                String value = fieldValue.asText();
                errorMessage = errorMessage.replace("{" + fieldName + "}", value);
                // Also support {statusString} as alias for {status}
                if ("status".equals(fieldName)) {
                    errorMessage = errorMessage.replace("{statusString}", value);
                }
            } else if (fieldValue.isNumber() || fieldValue.isBoolean()) {
                String value = fieldValue.asText();
                errorMessage = errorMessage.replace("{" + fieldName + "}", value);
            }
        }
        
        // Replace any remaining entity placeholders (e.g., {case_id})
        if (entities != null) {
            for (Map.Entry<String, String> entry : entities.entrySet()) {
                errorMessage = errorMessage.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        return errorMessage;
    }
    
    /**
     * Replace placeholders in message template with actual values
     */
    private String replacePlaceholdersInMessage(String template, Map<String, String> primaryValues, Map<String, String> fallbackValues) {
        if (template == null) {
            return null;
        }
        
        String message = template;
        
        // Replace with primary values first
        if (primaryValues != null) {
            for (Map.Entry<String, String> entry : primaryValues.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        // Replace any remaining placeholders with fallback values
        if (fallbackValues != null) {
            for (Map.Entry<String, String> entry : fallbackValues.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        return message;
    }
}

