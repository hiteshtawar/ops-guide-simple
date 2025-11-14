package com.lca.productionsupport.service;

import com.lca.productionsupport.config.DownstreamServiceProperties;
import com.lca.productionsupport.config.WebClientRegistry;
import com.lca.productionsupport.model.StepExecutionRequest;
import com.lca.productionsupport.model.StepExecutionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
        
        stepExecutionService = new StepExecutionService(webClientRegistry, runbookRegistry, runbookAdapter);
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
        assertTrue(response.getErrorMessage().contains("Downstream service not configured"));
        assertTrue(response.getErrorMessage().contains("unknown-service"));
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
        assertEquals("Step not found", response.getErrorMessage());
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
        assertEquals("Step not found", response.getErrorMessage());
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
        assertEquals("Step not found", response.getErrorMessage());
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
        assertEquals("Step not found", response.getErrorMessage());
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
        assertEquals("Step not found", response.getErrorMessage());
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
            assertFalse(response.getErrorMessage().contains("Downstream service not configured"));
        }
    }

    // ========== Entity Handling Tests ==========

    @Test
    void executeStep_withMultipleEntities_acceptsRequest() {
        Map<String, String> entities = new HashMap<>();
        entities.put("sampleId", "550e8400-e29b-41d4-a716-446655440000");
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
            .stepNumber(1)
            .entities(Map.of())
            .userId("user123")
            .authToken("token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertFalse(response.getSuccess());
        assertNotNull(response.getStepNumber());
        assertNotNull(response.getErrorMessage());
        assertNotNull(response.getDurationMs());
        assertNull(response.getResponseBody());
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
            .entities(Map.of("sampleId", "550e8400-e29b-41d4-a716-446655440000", "user_id", "user123", "sampleStatus", "Completed - Microtomy"))
            .userId("user123")
            .authToken("token")
            .build();

        StepExecutionResponse response = stepExecutionService.executeStep(request);

        assertNotNull(response);
        assertEquals(1, response.getStepNumber());
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
}
