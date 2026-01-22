package com.lca.productionsupport.controller;

import com.lca.productionsupport.model.OperationalRequest;
import com.lca.productionsupport.model.OperationalResponse;
import com.lca.productionsupport.model.OperationalResponse.RunbookStep;
import com.lca.productionsupport.model.StepExecutionRequest;
import com.lca.productionsupport.model.StepExecutionResponse;
import com.lca.productionsupport.model.TaskInfo;
import com.lca.productionsupport.service.ProductionSupportOrchestrator;
import com.lca.productionsupport.service.StepExecutionService;
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
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        summary = "Get Available Tasks",
        description = "Returns list of all supported task types. Useful for UI to display options when automatic pattern detection fails or for manual task selection."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved available tasks",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @GetMapping("/tasks")
    public ResponseEntity<List<TaskInfo>> getAvailableTasks() {
        List<TaskInfo> tasks = orchestrator.getAvailableTasks();
        return ResponseEntity.ok(tasks);
    }
    
    @Operation(
        summary = "Process Natural Language Query",
        description = "Accepts a natural language query, classifies the intent, extracts entities, and returns a structured runbook with executable steps. " +
                     "Examples: 'cancel case 2025123P6732', 'update case status to pending 2024123P6731'. " +
                     "Authentication handled by API Gateway."
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
        )
    })
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
        summary = "Execute Runbook Step",
        description = "Execute a specific step from a runbook. This will make the actual API call to the downstream service and return the result. " +
                     "Authentication handled by API Gateway."
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
            responseCode = "500",
            description = "Step execution failed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
        )
    })
    @PostMapping("/execute-step")
    public ResponseEntity<StepExecutionResponse> executeStep(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Step execution request with task details, step number, and required entities",
            required = true,
            content = @Content(schema = @Schema(implementation = StepExecutionRequest.class))
        )
        @Valid @RequestBody StepExecutionRequest request,
        @Parameter(description = "User role from API Gateway header")
        @RequestHeader(value = "Role-Name", required = false) String roleName,
        @Parameter(description = "API User from API Gateway header")
        @RequestHeader(value = "Api-User", required = false) String apiUser,
        @Parameter(description = "Lab ID from API Gateway header")
        @RequestHeader(value = "Lab-Id", required = false) String labId,
        @Parameter(description = "Discipline Name from API Gateway header")
        @RequestHeader(value = "Discipline-Name", required = false) String disciplineName,
        @Parameter(description = "Time Zone from API Gateway header")
        @RequestHeader(value = "Time-Zone", required = false) String timeZone,
        @RequestHeader(value = "accept", required = false) String accept
    ) {
        log.info("Executing step {} for task {} with role: {}, Api-User: {}", 
                request.getStepNumber(), request.getTaskId(), roleName, apiUser);
        
        // Set the user role from header if not already set in request body
        if (roleName != null && !roleName.isEmpty()) {
            request.setUserRole(roleName);
        }
        
        // Collect custom headers from API Gateway to forward to downstream service
        Map<String, String> customHeaders = new HashMap<>();
        if (apiUser != null && !apiUser.isEmpty()) {
            customHeaders.put("Api-User", apiUser);
            log.info("Added Api-User to customHeaders: {}", apiUser);
        } 
        if (labId != null && !labId.isEmpty()) {
            customHeaders.put("Lab-Id", labId);
        }
        if (disciplineName != null && !disciplineName.isEmpty()) {
            customHeaders.put("Discipline-Name", disciplineName);
        }
        if (timeZone != null && !timeZone.isEmpty()) {
            customHeaders.put("Time-Zone", timeZone);
        }
        if (roleName != null && !roleName.isEmpty()) {
            customHeaders.put("Role-Name", roleName);
        }
        if (accept != null && !accept.isEmpty()) {
            customHeaders.put("accept", accept);
        }
        log.debug("Custom headers map: {}", customHeaders);

        request.setCustomHeaders(customHeaders);

        StepExecutionResponse response = stepExecutionService.executeStep(request);
        
        return ResponseEntity.ok(response);
    }
    
}
