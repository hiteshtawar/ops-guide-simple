package com.opsguide.service;

import com.opsguide.model.OperationalResponse.RunbookStep;
import com.opsguide.model.StepExecutionRequest;
import com.opsguide.model.StepExecutionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Service to execute runbook steps by making actual API calls
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StepExecutionService {

    private final WebClient webClient;
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
        
        log.info("Executing step {}: {} {}", request.getStepNumber(), step.getMethod(), step.getPath());
        
        // Replace placeholders in path and body
        String resolvedPath = resolvePlaceholders(step.getPath(), request.getEntities());
        String resolvedBody = step.getRequestBody() != null ? 
            resolvePlaceholders(step.getRequestBody(), request.getEntities()) : null;
        
        try {
            // Build and execute the request
            String responseBody = executeHttpRequest(
                step.getMethod(),
                resolvedPath,
                resolvedBody,
                request.getAuthToken(),
                request.getUserId()
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
     * Execute HTTP request based on method
     */
    private String executeHttpRequest(String method, String path, String body, 
                                     String authToken, String userId) {
        
        WebClient.RequestHeadersSpec<?> request;
        
        switch (method.toUpperCase()) {
            case "GET":
                request = webClient.get().uri(path);
                break;
                
            case "POST":
                request = webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body != null ? body : "{}");
                break;
                
            case "PATCH":
                request = webClient.patch()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body != null ? body : "{}");
                break;
                
            case "PUT":
                request = webClient.put()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body != null ? body : "{}");
                break;
                
            case "DELETE":
                request = webClient.delete().uri(path);
                break;
                
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        
        // Add headers
        request = request
            .header("Authorization", "Bearer " + (authToken != null ? authToken : "dummy-token"))
            .header("X-User-ID", userId != null ? userId : "system")
            .header("X-Idempotency-Key", UUID.randomUUID().toString());
        
        // Execute and get response
        return request.retrieve()
            .onStatus(
                status -> status.isError(),
                response -> response.bodyToMono(String.class)
                    .flatMap(errorBody -> Mono.error(new RuntimeException("API Error: " + errorBody)))
            )
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(30))
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

