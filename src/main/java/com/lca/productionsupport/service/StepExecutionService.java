package com.lca.productionsupport.service;

import com.lca.productionsupport.config.WebClientRegistry;
import com.lca.productionsupport.model.OperationalResponse.RunbookStep;
import com.lca.productionsupport.model.StepExecutionRequest;
import com.lca.productionsupport.model.StepExecutionResponse;
import com.lca.productionsupport.model.StepMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
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
    private final RunbookParser runbookParser;
    
    /**
     * Execute a specific step
     */
    public StepExecutionResponse executeStep(StepExecutionRequest request) {
        long startTime = System.currentTimeMillis();
        
        // Get the step definition from runbook
        RunbookStep step = runbookParser.getStep(request.getTaskId(), request.getStepNumber());
        
        if (step == null) {
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .errorMessage("Step not found")
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        }
        
        StepMethod method = step.getMethod();
        
        // Check if this is a local execution step (no downstream service needed)
        if (method != null && method.isLocalExecution()) {
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
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .errorMessage("Downstream service not configured: " + request.getDownstreamService())
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
                request.getCustomHeaders(),
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
            
            return StepExecutionResponse.builder()
                .success(false)
                .stepNumber(request.getStepNumber())
                .stepDescription(step.getDescription())
                .errorMessage(e.getMessage())
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
        String expectedValue = step.getRequestBody();  // Expected value stored in requestBody field
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
        
        // Add standard headers
        request = request
            .header("Authorization", "Bearer " + (authToken != null ? authToken : "dummy-token"))
            .header("X-User-ID", userId != null ? userId : "system")
            .header("X-Idempotency-Key", UUID.randomUUID().toString());
        
        // Add custom headers from API Gateway (Api-User, Lab-Id, Discipline-Name, Time-Zone, Role-Name, accept, etc.)
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
}

