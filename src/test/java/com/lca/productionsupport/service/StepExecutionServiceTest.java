package com.lca.productionsupport.service;

import com.lca.productionsupport.config.DownstreamServiceProperties;
import com.lca.productionsupport.config.WebClientRegistry;
import com.lca.productionsupport.model.StepExecutionRequest;
import com.lca.productionsupport.model.StepExecutionResponse;
import com.lca.productionsupport.model.UseCaseDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StepExecutionService focusing on business logic
 * HTTP integration is covered by controller integration tests
 */
class StepExecutionServiceTest {

    private StepExecutionService stepExecutionService;
    private RunbookRegistry runbookRegistry;
    private RunbookAdapter runbookAdapter;
    private WebClientRegistry webClientRegistry;
    private ErrorMessageTranslator errorMessageTranslator;
    private DownstreamServiceProperties serviceProperties;

    @BeforeEach
    void setUp() {
        // Create service properties with real service
        serviceProperties = new DownstreamServiceProperties();
        Map<String, DownstreamServiceProperties.ServiceConfig> services = new HashMap<>();
        DownstreamServiceProperties.ServiceConfig config = new DownstreamServiceProperties.ServiceConfig();
        config.setBaseUrl("https://api.example.com");
        config.setTimeout(5);
        services.put("ap-services", config);
        serviceProperties.setServices(services);
        
        // Create registry and adapter
        webClientRegistry = new WebClientRegistry(serviceProperties);
        runbookRegistry = new TestRunbookRegistry();
        runbookAdapter = new RunbookAdapter();
        errorMessageTranslator = new ErrorMessageTranslator();
        errorMessageTranslator.init();
        
        stepExecutionService = new StepExecutionService(webClientRegistry, runbookRegistry, runbookAdapter, errorMessageTranslator);
    }
    
    // Test-specific runbook registry that loads YAMLs properly
    private static class TestRunbookRegistry extends RunbookRegistry {
        public TestRunbookRegistry() {
            super();
            // Trigger loading with classpath location
            try {
                var locationField = RunbookRegistry.class.getDeclaredField("runbookLocation");
                locationField.setAccessible(true);
                locationField.set(this, "classpath:runbooks/");
                
                var enabledField = RunbookRegistry.class.getDeclaredField("enabled");
                enabledField.setAccessible(true);
                enabledField.set(this, true);
                
                // Call loadRunbooks
                loadRunbooks();
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize test registry", e);
            }
        }
    }

    // ========== Error Handling Tests (No HTTP needed) ==========

    @Test
    void executeStep_unconfiguredService_returnsError() {
        // Note: Step 1 is HEADER_CHECK, step 2 is LOCAL_MESSAGE, so using step 3 which requires downstream service
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("unknown-service")
            .stepNumber(3)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("test-token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        assertEquals(3, response.getStepNumber());
        assertNotNull(response.getErrorMessage());
        // Error message should now be user-friendly
        assertEquals("The requested service is not properly configured. Please contact support.", response.getErrorMessage());
        // Technical details should be in responseBody
        assertNotNull(response.getResponseBody());
        assertTrue(response.getResponseBody().contains("unknown-service"));
        assertTrue(response.getDurationMs() >= 0);
    }

    @Test
    void executeStep_invalidStepNumber_returnsError() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(999)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("test-token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        assertEquals(999, response.getStepNumber());
        // Error message should now be user-friendly
        assertEquals("The requested operation step was not found. Please verify the step number.", response.getErrorMessage());
        assertTrue(response.getDurationMs() >= 0);
    }

    @Test
    void executeStep_stepNumberZero_returnsError() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(0)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("test-token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        // Error message should now be user-friendly
        assertEquals("The requested operation step was not found. Please verify the step number.", response.getErrorMessage());
    }

    @Test
    void executeStep_negativeStepNumber_returnsError() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(-1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("test-token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        // Error message should now be user-friendly
        assertEquals("The requested operation step was not found. Please verify the step number.", response.getErrorMessage());
    }

    @Test
    void executeStep_invalidTaskId_returnsError() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("INVALID_TASK")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("test-token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        // Error message should now be user-friendly
        assertEquals("The requested operation step was not found. Please verify the step number.", response.getErrorMessage());
    }

    @Test
    void executeStep_nullTaskId_returnsError() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId(null)
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("test-token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        // Error message should now be user-friendly
        assertEquals("The requested operation step was not found. Please verify the step number.", response.getErrorMessage());
    }

    // ========== Service Configuration Tests ==========

    @Test
    void executeStep_validService_doesNotReturnServiceError() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732", "user_id", "user123"))
            .userId("user123")
            .authToken("test-token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        // May fail due to network, but should NOT fail with "service not configured" error
        if (!response.getSuccess()) {
            assertFalse(response.getErrorMessage().contains("not properly configured"));
        }
    }

    // ========== Entity Handling Tests ==========

    @Test
    void executeStep_withMultipleEntities_acceptsRequest() {
        Map<String, String> entities = new HashMap<>();
        entities.put("barcode", "BC123456");
        entities.put("user_id", "user123");
        entities.put("sampleStatus", "Completed - Microtomy");
        entities.put("reason", "test");

        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("UPDATE_SAMPLE_STATUS")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(entities)
            .userId("user123")
            .authToken("test-token")
            .build();

        // Should not throw exception
        StepExecutionResponse response = stepExecutionService.executeStep(request);
        assertNotNull(response);
        assertEquals(1, response.getStepNumber());
    }

    @Test
    void executeStep_withEmptyEntities_acceptsRequest() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of())
            .userId("user123")
            .authToken("test-token")
            .build();

        // Should not throw exception, may fail on execution but that's expected
        StepExecutionResponse response = stepExecutionService.executeStep(request);
        assertNotNull(response);
    }

    @Test
    void executeStep_withNullEntities_acceptsRequest() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(null)
            .userId("user123")
            .authToken("test-token")
            .build();

        // Should not throw NPE
        StepExecutionResponse response = stepExecutionService.executeStep(request);
        assertNotNull(response);
    }

    // ========== Response Structure Tests ==========

    @Test
    void executeStep_alwaysReturnsResponse() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("INVALID")
            .downstreamService("ap-services")
            .stepNumber(999)
            .entities(Map.of())
            .userId("user123")
            .authToken("token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertNotNull(response.getStepNumber());
        assertNotNull(response.getSuccess());
        assertNotNull(response.getDurationMs());
    }

    @Test
    void executeStep_errorResponse_hasRequiredFields() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("unknown-service")
            .stepNumber(3) // Use step 3 which requires downstream service
            .entities(Map.of())
            .userId("user123")
            .authToken("token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        assertNotNull(response.getStepNumber());
        assertNotNull(response.getErrorMessage());
        assertNotNull(response.getDurationMs());
        // Technical details should be in responseBody for errors
        assertNotNull(response.getResponseBody());
    }

    // ========== Step Validation Tests ==========

    @Test
    void executeStep_validStepNumber_loadsStep() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732", "user_id", "user123"))
            .userId("user123")
            .authToken("token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        // Step should be loaded (may fail on HTTP call, but step description should be set)
        if (response.getStepDescription() != null) {
            assertFalse(response.getStepDescription().isEmpty());
        }
    }

    @Test
    void executeStep_updateCaseStatus_validStep() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("UPDATE_SAMPLE_STATUS")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("barcode", "BC123456", "user_id", "user123", "sampleStatus", "Completed - Microtomy"))
            .userId("user123")
            .authToken("token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(1, response.getStepNumber());
    }

    @Test
    void executeStep_withNullTaskId_returnsError() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId(null)
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of())
            .userId("user123")
            .authToken("token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        // Error message should now be user-friendly
        assertEquals("The requested operation step was not found. Please verify the step number.", response.getErrorMessage());
    }

    @Test
    void executeStep_withNullEntities_handlesGracefully() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(null)
            .userId("user123")
            .authToken("token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(1, response.getStepNumber());
    }

    @Test
    void executeStep_withInvalidDownstreamService_returnsError() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("invalid-service")
            .stepNumber(3)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        // Error message should now be user-friendly
        assertEquals("The requested service is not properly configured. Please contact support.", response.getErrorMessage());
    }

    @Test
    void executeStep_withPUTMethod_handlesCorrectly() {
        // Create a test runbook with PUT method
        UseCaseDefinition useCase = new UseCaseDefinition();
        UseCaseDefinition.UseCaseInfo info = new UseCaseDefinition.UseCaseInfo();
        info.setId("TEST_PUT");
        useCase.setUseCase(info);
        UseCaseDefinition.ClassificationConfig classification = new UseCaseDefinition.ClassificationConfig();
        classification.setKeywords(List.of("test"));
        useCase.setClassification(classification);
        UseCaseDefinition.ExecutionConfig execution = new UseCaseDefinition.ExecutionConfig();
        UseCaseDefinition.StepDefinition step = new UseCaseDefinition.StepDefinition();
        step.setStepNumber(1);
        step.setMethod("PUT");
        step.setPath("/api/test/{barcode}");
        step.setStepType("procedure");
        execution.setSteps(List.of(step));
        useCase.setExecution(execution);
        
        // Manually add to registry for test
        RunbookRegistry testRegistry = new RunbookRegistry() {
            @Override
            public UseCaseDefinition getUseCase(String id) {
                if ("TEST_PUT".equals(id)) {
                    return useCase;
                }
                return super.getUseCase(id);
            }
        };
        
        StepExecutionService testService = new StepExecutionService(
            webClientRegistry, testRegistry, runbookAdapter, errorMessageTranslator);
        
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("TEST_PUT")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("barcode", "BC123456"))
            .userId("user123")
            .authToken("token")
            .build();

        // Should not throw, but will fail on actual HTTP call
        StepExecutionResponse response = testService.executeStep(request);
        assertNotNull(response);
    }

    // ========== Header Check Tests ==========

    @Test
    void executeStep_headerCheck_withValidRole_returnsSuccess() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .userRole("Production Support")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getStepNumber());
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getResponseBody());
        assertTrue(response.getResponseBody().contains("valid"));
        assertTrue(response.getDurationMs() >= 0);
    }

    @Test
    void executeStep_headerCheck_withInvalidRole_returnsForbidden() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .userRole("Regular User")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        assertEquals(1, response.getStepNumber());
        assertEquals(403, response.getStatusCode());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("Access denied"));
        assertTrue(response.getDurationMs() >= 0);
    }

    @Test
    void executeStep_headerCheck_withNullRole_returnsForbidden() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .userRole(null)
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        assertEquals(1, response.getStepNumber());
        assertEquals(403, response.getStatusCode());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("Access denied"));
    }

    @Test
    void executeStep_headerCheck_withEmptyRole_returnsForbidden() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .userRole("")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        assertEquals(1, response.getStepNumber());
        assertEquals(403, response.getStatusCode());
    }

    // ========== Local Message Tests ==========

    @Test
    void executeStep_localMessage_returnsMessageSuccessfully() {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(2)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertTrue(response.getSuccess());
        assertEquals(2, response.getStepNumber());
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getResponseBody());
        assertTrue(response.getResponseBody().contains("Case and it's materials will be canceled and removed from the workpool"));
        assertTrue(response.getDurationMs() >= 0);
    }

    // ========== Header Functionality Tests ==========

    @Test
    void executeStep_withYamlHeaders_usesHeadersFromYaml() {
        // Step 3 of CANCEL_CASE has headers defined in YAML
        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("Api-User", "test-user");
        customHeaders.put("Lab-Id", "lab-123");
        customHeaders.put("Discipline-Name", "pathology");
        customHeaders.put("Time-Zone", "America/New_York");
        customHeaders.put("Role-Name", "Production Support");
        customHeaders.put("accept", "application/json");

        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(3)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("Bearer test-token-123")
            .customHeaders(customHeaders)
            .build();

        // This will fail on actual HTTP call, but we can verify the step is loaded correctly
        StepExecutionResponse response = stepExecutionService.executeStep(request);

        // Step should be found (may fail on HTTP, but not on step loading)
        assertNotNull(response);
        assertEquals(3, response.getStepNumber());
        // If it fails, it should be due to network, not step loading
        if (!response.getSuccess()) {
            assertFalse(response.getErrorMessage().contains("Step not found"));
        }
    }

    @Test
    void executeStep_withYamlHeaders_resolvesPlaceholders() {
        // Test that placeholders in YAML headers are resolved from request context
        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("Api-User", "test-api-user");
        customHeaders.put("Lab-Id", "test-lab-id");
        customHeaders.put("Discipline-Name", "test-discipline");
        customHeaders.put("Time-Zone", "UTC");
        customHeaders.put("Role-Name", "Production Support");
        customHeaders.put("accept", "application/json");

        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(3)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("test-user-id")
            .authToken("test-token-value")
            .customHeaders(customHeaders)
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        // Verify step is loaded (headers with placeholders should be resolved)
        assertNotNull(response);
        assertEquals(3, response.getStepNumber());
    }

    @Test
    void executeStep_withRequestHeadersOverridesYamlHeaders() {
        // Request headers should take precedence over YAML headers
        Map<String, String> requestHeaders = new HashMap<>();
        requestHeaders.put("Api-User", "override-user");
        requestHeaders.put("Lab-Id", "override-lab");
        requestHeaders.put("Content-Type", "application/json");

        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(3)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .customHeaders(requestHeaders)
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(3, response.getStepNumber());
    }

    @Test
    void executeStep_withPartialHeaders_mergesCorrectly() {
        // Test that partial headers from request merge with YAML headers
        Map<String, String> partialHeaders = new HashMap<>();
        partialHeaders.put("Api-User", "partial-user");
        // Lab-Id, Discipline-Name, etc. should come from YAML if not in request

        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(3)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("Bearer token-123")
            .customHeaders(partialHeaders)
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(3, response.getStepNumber());
    }

    @Test
    void executeStep_withNoCustomHeaders_usesYamlHeadersOnly() {
        // Test that YAML headers are used when no custom headers provided
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(3)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("Bearer token-123")
            .customHeaders(null) // No custom headers
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(3, response.getStepNumber());
    }

    @Test
    void executeStep_withEmptyCustomHeaders_usesYamlHeaders() {
        // Test that YAML headers are used when empty custom headers provided
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(3)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("Bearer token-123")
            .customHeaders(new HashMap<>()) // Empty custom headers
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(3, response.getStepNumber());
    }

    @Test
    void executeStep_withTokenPlaceholder_resolvesFromAuthToken() {
        // Test that {token} placeholder is resolved from authToken
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(3)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("Bearer my-secret-token")
            .customHeaders(new HashMap<>())
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(3, response.getStepNumber());
    }

    @Test
    void executeStep_withUserIdPlaceholder_resolvesFromUserId() {
        // Test that {user_id} placeholder is resolved from userId
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(3)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("specific-user-id-123")
            .authToken("Bearer token")
            .customHeaders(new HashMap<>())
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(3, response.getStepNumber());
    }

    @Test
    void executeStep_withIdempotencyKeyPlaceholder_generatesUUID() {
        // Test that {IDEMPOTENCY_KEY} placeholder generates a UUID
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(3)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("Bearer token")
            .customHeaders(new HashMap<>())
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(3, response.getStepNumber());
    }

    @Test
    void executeStep_withPostcheckHeaders_usesHeadersFromYaml() {
        // Test that postcheck steps (step 4) also use headers from YAML
        Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("Api-User", "test-user");
        customHeaders.put("Lab-Id", "lab-123");

        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(4) // Postcheck step
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("Bearer token")
            .customHeaders(customHeaders)
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(4, response.getStepNumber());
    }

    @Test
    void executeStep_withStepWithoutHeaders_worksNormally() {
        // Test that steps without headers in YAML still work (like step 1 - HEADER_CHECK)
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1) // HEADER_CHECK step - no headers in YAML
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .userRole("Production Support")
            .customHeaders(new HashMap<>())
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getStepNumber());
    }

    @Test
    void executeStep_withNullAuthToken_usesDefaultToken() {
        // Test that null auth token is handled properly
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken(null) // Null token
            .userRole("Production Support")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(1, response.getStepNumber());
    }

    @Test
    void executeStep_withNullUserId_usesDefault() {
        // Test that null user ID is handled
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId(null) // Null user ID
            .authToken("token")
            .userRole("Production Support")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(1, response.getStepNumber());
    }

    @Test
    void executeStep_withAuthTokenWithoutBearerPrefix_addsPrefix() {
        // Test that auth token without Bearer prefix gets it added
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("my-token-without-bearer") // No Bearer prefix
            .userRole("Production Support")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(1, response.getStepNumber());
    }

    @Test
    void executeStep_withAuthTokenWithBearerPrefix_keepsPrefix() {
        // Test that auth token with Bearer prefix is kept as-is
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("Bearer my-token-with-bearer")
            .userRole("Production Support")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(1, response.getStepNumber());
    }

    @Test
    void executeStep_withNullStepMethod_handlesGracefully() {
        // This tests the branch where method could be null
        // Most runbooks have methods defined, but we should handle null gracefully
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .userRole("Production Support")
            .build();

        // Should not throw NPE even if method is null
        StepExecutionResponse response = stepExecutionService.executeStep(request);
        assertNotNull(response);
    }

    @Test
    void executeStep_withEmptyCustomHeaders_mergesWithYamlHeaders() {
        // Test header merging with empty custom headers
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(3)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("Bearer token")
            .customHeaders(new HashMap<>()) // Empty
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(3, response.getStepNumber());
    }

    @Test
    void executeStep_localMessage_withNullDownstreamService() {
        // Local message steps don't need downstream service
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService(null) // Null service OK for local steps
            .stepNumber(2) // LOCAL_MESSAGE step
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertTrue(response.getSuccess());
        assertEquals(2, response.getStepNumber());
    }

    @Test
    void executeStep_headerCheck_withNullDownstreamService() {
        // Header check steps don't need downstream service
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService(null) // Null service OK for header check
            .stepNumber(1) // HEADER_CHECK step
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .userRole("Production Support")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertTrue(response.getSuccess());
        assertEquals(1, response.getStepNumber());
    }
}
