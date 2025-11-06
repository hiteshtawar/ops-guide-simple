package com.productionsupport.controller;

import com.productionsupport.model.OperationalRequest;
import com.productionsupport.model.OperationalResponse;
import com.productionsupport.model.OperationalResponse.RunbookStep;
import com.productionsupport.model.StepExecutionRequest;
import com.productionsupport.model.StepExecutionResponse;
import com.productionsupport.service.ProductionSupportOrchestrator;
import com.productionsupport.service.StepExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Main REST API controller for Production Support
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Production Support", description = "Operational automation API for production support tasks")
public class ProductionSupportController {

    private final ProductionSupportOrchestrator orchestrator;
    private final StepExecutionService stepExecutionService;
    
    @Operation(
        summary = "Health Check",
        description = "Check if the service is running and healthy"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Service is healthy",
            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE, schema = @Schema(implementation = String.class))
        )
    })
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
    
    @Operation(
        summary = "Process Natural Language Query",
        description = "Accepts a natural language query, classifies the intent, extracts entities, and returns a structured runbook with executable steps. " +
                     "Examples: 'cancel case 2025123P6732', 'update case status to pending 2024123P6731'. " +
                     "Requires 'production_support' or 'support_admin' role."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully processed and classified the query",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = OperationalResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request - query is required and cannot be empty",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - insufficient permissions",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PreAuthorize("hasAnyRole('production_support', 'support_admin')")
    @PostMapping("/process")
    public ResponseEntity<OperationalResponse> processRequest(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Operational request containing natural language query and user information",
            required = true,
            content = @Content(schema = @Schema(implementation = OperationalRequest.class))
        )
        @Valid @RequestBody OperationalRequest request
    ) {
        log.info("Received request: {}", request.getQuery());
        
        OperationalResponse response = orchestrator.processRequest(request);
        
        if ("UNKNOWN".equals(response.getTaskId())) {
            log.warn("Could not classify request: {}", request.getQuery());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Get Runbook Steps",
        description = "Retrieve runbook steps for a specific task. Optionally filter by stage (precheck, procedure, postcheck, rollback)."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved steps",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = RunbookStep.class)
            )
        )
    })
    @GetMapping("/tasks/{taskId}/steps")
    public ResponseEntity<List<RunbookStep>> getSteps(
        @Parameter(
            description = "Task identifier (e.g., CANCEL_CASE, UPDATE_CASE_STATUS)",
            required = true,
            example = "CANCEL_CASE"
        )
        @PathVariable String taskId,
        
        @Parameter(
            description = "Filter steps by stage (precheck, procedure, postcheck, rollback)",
            required = false,
            example = "precheck"
        )
        @RequestParam(required = false) String stage
    ) {
        log.info("Getting steps for task: {}, stage: {}", taskId, stage);
        
        List<RunbookStep> steps = orchestrator.getStepsForStage(taskId, stage);
        
        return ResponseEntity.ok(steps);
    }
    
    @Operation(
        summary = "Execute Runbook Step",
        description = "Execute a specific step from a runbook. This will make the actual API call to the downstream service and return the result. " +
                     "Requires 'production_support' or 'support_admin' role."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Step executed successfully",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = StepExecutionResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request - taskId and stepNumber are required",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - insufficient permissions",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Step execution failed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PreAuthorize("hasAnyRole('production_support', 'support_admin')")
    @PostMapping("/execute-step")
    public ResponseEntity<StepExecutionResponse> executeStep(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Step execution request with task details, step number, and required entities",
            required = true,
            content = @Content(schema = @Schema(implementation = StepExecutionRequest.class))
        )
        @Valid @RequestBody StepExecutionRequest request
    ) {
        log.info("Executing step {} for task {}", request.getStepNumber(), request.getTaskId());
        
        StepExecutionResponse response = stepExecutionService.executeStep(request);
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(
        summary = "Classify Query Only",
        description = "Lightweight endpoint that classifies the query and extracts entities without returning full runbook steps. " +
                     "Useful for preview or validation purposes."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully classified the query",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = OperationalResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request - query is required",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping("/classify")
    public ResponseEntity<OperationalResponse> classifyOnly(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Operational request containing the natural language query to classify",
            required = true,
            content = @Content(schema = @Schema(implementation = OperationalRequest.class))
        )
        @Valid @RequestBody OperationalRequest request
    ) {
        log.info("Classifying query: {}", request.getQuery());
        
        // Process but only return classification, not full steps
        OperationalResponse response = orchestrator.processRequest(request);
        
        // Clear steps to return lightweight response
        response.setSteps(null);
        
        return ResponseEntity.ok(response);
    }
}
