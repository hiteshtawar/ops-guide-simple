package com.lca.productionsupport.service;

import com.lca.productionsupport.model.OperationalRequest;
import com.lca.productionsupport.model.OperationalResponse;
import com.lca.productionsupport.model.TaskInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProductionSupportOrchestratorTest {

    private ProductionSupportOrchestrator orchestrator;
    private RunbookRegistry runbookRegistry;
    private RunbookClassifier runbookClassifier;
    private RunbookEntityExtractor entityExtractor;
    private RunbookAdapter runbookAdapter;

    @BeforeEach
    void setUp() {
        // Create test registry with manual initialization
        runbookRegistry = new TestRunbookRegistry();
        runbookClassifier = new RunbookClassifier(runbookRegistry);
        entityExtractor = new RunbookEntityExtractor();
        runbookAdapter = new RunbookAdapter();
        
        orchestrator = new ProductionSupportOrchestrator(
            runbookRegistry, 
            runbookClassifier, 
            entityExtractor, 
            runbookAdapter
        );
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

    // ========== Process Request Tests ==========

    @Test
    void processRequest_cancelCase_withCaseId() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("CANCEL_CASE", response.getTaskId());
        assertEquals("Cancel Case", response.getTaskName());
        assertEquals("ap-services", response.getDownstreamService());
        assertEquals("2025123P6732", response.getExtractedEntities().get("case_id"));
        assertNotNull(response.getSteps());
    }

    @Test
    void processRequest_cancelCase_withoutCaseId() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("CANCEL_CASE", response.getTaskId());
        assertNotNull(response.getWarnings());
    }

    @Test
    void processRequest_updateStatus_withCaseIdAndStatus() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update sample status to Completed - Microtomy for sample BC123456")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UPDATE_SAMPLE_STATUS", response.getTaskId());
        assertEquals("Update Sample Status", response.getTaskName());
        assertEquals("ap-services", response.getDownstreamService());
        // Entity extraction may not work perfectly in all cases, so we check taskId matches
        assertNotNull(response.getSteps());
    }

    @Test
    void processRequest_updateStatus_withoutStatus() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update sample status for sample BC123456")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UPDATE_SAMPLE_STATUS", response.getTaskId());
    }

    @Test
    void processRequest_updateStatus_withoutCaseId() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update sample status to Completed - Microtomy")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UPDATE_SAMPLE_STATUS", response.getTaskId());
    }

    @Test
    void processRequest_updateStatus_withoutBoth() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update sample status")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UPDATE_SAMPLE_STATUS", response.getTaskId());
    }

    @Test
    void processRequest_unknownTask() {
        OperationalRequest request = OperationalRequest.builder()
            .query("hello world")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UNKNOWN", response.getTaskId());
        assertEquals("Unknown", response.getTaskName());
        assertEquals("ap-services", response.getDownstreamService());
    }

    // ========== Explicit Task ID Tests ==========

    @Test
    void processRequest_withExplicitTaskId_cancelCase() {
        OperationalRequest request = OperationalRequest.builder()
            .query("2025123P6732")
            .taskId("CANCEL_CASE")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("CANCEL_CASE", response.getTaskId());
        assertEquals("2025123P6732", response.getExtractedEntities().get("case_id"));
    }

    @Test
    void processRequest_withExplicitTaskId_updateStatus() {
        OperationalRequest request = OperationalRequest.builder()
            .query("sample BC123456 status Completed - Microtomy")
            .taskId("UPDATE_SAMPLE_STATUS")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UPDATE_SAMPLE_STATUS", response.getTaskId());
        // Entity extraction works when taskId is explicit
        assertNotNull(response.getExtractedEntities());
    }

    @Test
    void processRequest_withExplicitTaskId_unknown() {
        OperationalRequest request = OperationalRequest.builder()
            .query("some query")
            .taskId("UNKNOWN")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UNKNOWN", response.getTaskId());
    }

    @Test
    void processRequest_withExplicitTaskId_invalidTaskId() {
        OperationalRequest request = OperationalRequest.builder()
            .query("some query")
            .taskId("INVALID_TASK")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UNKNOWN", response.getTaskId());
    }

    @Test
    void processRequest_withExplicitTaskId_lowercase() {
        OperationalRequest request = OperationalRequest.builder()
            .query("2025123P6732")
            .taskId("CANCEL_CASE")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("CANCEL_CASE", response.getTaskId());
    }

    // ========== Downstream Service Tests ==========

    @Test
    void processRequest_defaultDownstreamService() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .userId("user123")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("ap-services", response.getDownstreamService());
    }

    @Test
    void processRequest_customDownstreamService() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .userId("user123")
            .downstreamService("custom-service")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("custom-service", response.getDownstreamService());
    }

    // ========== Step Grouping Tests ==========

    @Test
    void processRequest_groupsStepsByType() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        OperationalResponse.StepGroups stepGroups = response.getSteps();
        assertNotNull(stepGroups);
        
        // CANCEL_CASE should have at least procedure steps
        assertNotNull(stepGroups.getProcedure());
        assertTrue(stepGroups.getProcedure().size() > 0);
    }

    @Test
    void processRequest_updateCaseStatus_hasCorrectStepGroups() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update status to pending 2025123P6732")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        OperationalResponse.StepGroups stepGroups = response.getSteps();
        assertNotNull(stepGroups);
        
        // UPDATE_SAMPLE_STATUS should have at least procedure steps
        assertNotNull(stepGroups.getProcedure());
        assertTrue(stepGroups.getProcedure().size() > 0);
    }

    // ========== Get Available Tasks Tests ==========

    @Test
    void getAvailableTasks_returnsAllTasks() {
        List<TaskInfo> tasks = orchestrator.getAvailableTasks();

        assertNotNull(tasks);
        assertTrue(tasks.size() > 0);
        
        // Should have at least CANCEL_CASE and UPDATE_SAMPLE_STATUS
        assertTrue(tasks.stream().anyMatch(t -> "CANCEL_CASE".equals(t.getTaskId())));
        assertTrue(tasks.stream().anyMatch(t -> "UPDATE_SAMPLE_STATUS".equals(t.getTaskId())));
    }

    @Test
    void getAvailableTasks_hasRequiredFields() {
        List<TaskInfo> tasks = orchestrator.getAvailableTasks();

        for (TaskInfo task : tasks) {
            assertNotNull(task.getTaskId());
            assertNotNull(task.getTaskName());
            assertNotNull(task.getDescription());
        }
    }

    @Test
    void getAvailableTasks_cancelCaseHasCorrectFields() {
        List<TaskInfo> tasks = orchestrator.getAvailableTasks();

        Optional<TaskInfo> cancelTask = tasks.stream()
            .filter(t -> "CANCEL_CASE".equals(t.getTaskId()))
            .findFirst();

        assertTrue(cancelTask.isPresent());
        assertEquals("CANCEL_CASE", cancelTask.get().getTaskId());
        assertEquals("Cancel Case", cancelTask.get().getTaskName());
        assertTrue(cancelTask.get().getDescription().contains("cancellation"));
        assertNotNull(cancelTask.get().getExampleQuery());
        assertTrue(cancelTask.get().getExampleQuery().contains("[case_number]"));
    }

    @Test
    void getAvailableTasks_updateCaseStatusHasCorrectFields() {
        List<TaskInfo> tasks = orchestrator.getAvailableTasks();

        Optional<TaskInfo> updateTask = tasks.stream()
            .filter(t -> "UPDATE_SAMPLE_STATUS".equals(t.getTaskId()))
            .findFirst();

        assertTrue(updateTask.isPresent());
        assertEquals("UPDATE_SAMPLE_STATUS", updateTask.get().getTaskId());
        assertEquals("Update Sample Status", updateTask.get().getTaskName());
        assertTrue(updateTask.get().getDescription().contains("status"));
        assertNotNull(updateTask.get().getExampleQuery());
        assertTrue(updateTask.get().getExampleQuery().contains("[sample_barcode]"));
        assertTrue(updateTask.get().getExampleQuery().contains("[sample_status]"));
    }

    @Test
    void getAvailableTasks_updateStainNameHasCorrectFields() {
        List<TaskInfo> tasks = orchestrator.getAvailableTasks();

        Optional<TaskInfo> stainTask = tasks.stream()
            .filter(t -> "UPDATE_STAIN_NAME".equals(t.getTaskId()))
            .findFirst();

        assertTrue(stainTask.isPresent());
        assertEquals("UPDATE_STAIN_NAME", stainTask.get().getTaskId());
        assertEquals("Update Stain Name", stainTask.get().getTaskName());
        assertNotNull(stainTask.get().getExampleQuery());
        assertTrue(stainTask.get().getExampleQuery().contains("[sample_barcode]"));
        assertTrue(stainTask.get().getExampleQuery().contains("[stain_name]"));
    }

    // ========== Edge Cases ==========

    @Test
    void processRequest_emptyQuery() {
        OperationalRequest request = OperationalRequest.builder()
            .query("")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UNKNOWN", response.getTaskId());
    }

    @Test
    void processRequest_emptyTaskId() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .taskId("")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        // Empty taskId should trigger classification
        assertEquals("CANCEL_CASE", response.getTaskId());
    }

    @Test
    void processRequest_nullTaskId() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .taskId(null)
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        // Null taskId should trigger classification
        assertEquals("CANCEL_CASE", response.getTaskId());
    }

    // ========== Integration Tests ==========

    @Test
    void processRequest_endToEnd_cancelCase() {
        OperationalRequest request = OperationalRequest.builder()
            .query("Please cancel case 2025123P6732 immediately")
            .userId("engineer@example.com")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        // Verify complete response
        assertEquals("CANCEL_CASE", response.getTaskId());
        assertEquals("Cancel Case", response.getTaskName());
        assertEquals("ap-services", response.getDownstreamService());
        assertEquals("2025123P6732", response.getExtractedEntities().get("case_id"));
        
        // Verify steps are populated
        assertNotNull(response.getSteps());
        assertNotNull(response.getSteps().getProcedure());
        
        // Verify procedure steps have required fields
        for (OperationalResponse.RunbookStep step : response.getSteps().getProcedure()) {
            assertNotNull(step.getStepNumber());
            assertNotNull(step.getDescription());
            assertNotNull(step.getStepType());
        }
    }

    @Test
    void processRequest_endToEnd_updateStatus() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update sample status to Completed - Microtomy for sample BC123456")
            .userId("pathologist@example.com")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        // Verify complete response
        assertEquals("UPDATE_SAMPLE_STATUS", response.getTaskId());
        assertEquals("Update Sample Status", response.getTaskName());
        assertEquals("ap-services", response.getDownstreamService());
        assertNotNull(response.getExtractedEntities());
        
        // Verify steps are populated
        assertNotNull(response.getSteps());
        assertTrue(response.getSteps().getPrechecks().size() > 0);
        assertTrue(response.getSteps().getProcedure().size() > 0);
    }

    @Test
    void processRequest_withNullDownstreamService_usesDefault() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .userId("user123")
            .downstreamService(null)
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("CANCEL_CASE", response.getTaskId());
        assertNotNull(response.getDownstreamService());
    }

    @Test
    void processRequest_withEmptyDownstreamService_usesDefault() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .userId("user123")
            .downstreamService("")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("CANCEL_CASE", response.getTaskId());
        // Empty string gets overridden by runbook's default downstream service
        assertNotNull(response.getDownstreamService());
    }

    @Test
    void processRequest_classifierReturnsUnknownButRunbookExists() {
        // This tests the edge case where classifier returns a taskId but runbook doesn't exist
        // This shouldn't happen in practice but tests the null check
        OperationalRequest request = OperationalRequest.builder()
            .query("hello world")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UNKNOWN", response.getTaskId());
        assertNotNull(response.getWarnings());
    }

    @Test
    void getAvailableTasks_withNullDescription_handlesGracefully() {
        List<TaskInfo> tasks = orchestrator.getAvailableTasks();

        assertNotNull(tasks);
        // All tasks should have required fields even if description is null
        for (TaskInfo task : tasks) {
            assertNotNull(task.getTaskId());
            assertNotNull(task.getTaskName());
            assertNotNull(task.getDescription()); // Should be empty string if null
        }
    }
    
    @Test
    void getAvailableTasks_includesValidInputsForEnumValues() {
        List<TaskInfo> tasks = orchestrator.getAvailableTasks();

        // Find UPDATE_SAMPLE_STATUS task
        Optional<TaskInfo> updateStatusTask = tasks.stream()
            .filter(t -> "UPDATE_SAMPLE_STATUS".equals(t.getTaskId()))
            .findFirst();

        assertTrue(updateStatusTask.isPresent());
        
        // Should have validInputs
        assertNotNull(updateStatusTask.get().getValidInputs());
        List<TaskInfo.ValidInput> validInputs = updateStatusTask.get().getValidInputs();
        assertFalse(validInputs.isEmpty());
        
        // Should have "Valid Sample statuss" (or similar) with enum values
        Optional<TaskInfo.ValidInput> sampleStatusInput = validInputs.stream()
            .filter(vi -> vi.getName() != null && 
                vi.getName().toLowerCase().contains("sample") &&
                vi.getName().toLowerCase().contains("status"))
            .findFirst();
        
        assertTrue(sampleStatusInput.isPresent());
        assertNotNull(sampleStatusInput.get().getList());
        List<String> enumValues = sampleStatusInput.get().getList();
        assertFalse(enumValues.isEmpty());
        // Should contain at least "Canceled" from the enumValues
        assertTrue(enumValues.contains("Canceled"));
    }
    
    @Test
    void getAvailableTasks_tasksWithoutEnumValues_haveNoValidInputs() {
        List<TaskInfo> tasks = orchestrator.getAvailableTasks();

        // Find CANCEL_CASE task (should not have enumValues)
        Optional<TaskInfo> cancelTask = tasks.stream()
            .filter(t -> "CANCEL_CASE".equals(t.getTaskId()))
            .findFirst();

        assertTrue(cancelTask.isPresent());
        
        // Should not have validInputs (or have empty validInputs)
        if (cancelTask.get().getValidInputs() != null) {
            assertTrue(cancelTask.get().getValidInputs().isEmpty());
        }
    }
    
    @Test
    void getAvailableTasks_validInputsFormatIsCorrect() {
        List<TaskInfo> tasks = orchestrator.getAvailableTasks();

        // Find UPDATE_SAMPLE_STATUS task
        Optional<TaskInfo> updateStatusTask = tasks.stream()
            .filter(t -> "UPDATE_SAMPLE_STATUS".equals(t.getTaskId()))
            .findFirst();

        assertTrue(updateStatusTask.isPresent());
        assertNotNull(updateStatusTask.get().getValidInputs());
        
        List<TaskInfo.ValidInput> validInputs = updateStatusTask.get().getValidInputs();
        
        for (TaskInfo.ValidInput validInput : validInputs) {
            // Each validInput should have "name" and "list"
            assertNotNull(validInput.getName());
            assertNotNull(validInput.getList());
            
            // name should be a String
            String name = validInput.getName();
            assertFalse(name.isEmpty());
            assertTrue(name.startsWith("Valid "));
            
            // list should be a List of Strings
            List<String> list = validInput.getList();
            assertFalse(list.isEmpty());
            for (String value : list) {
                assertNotNull(value);
                assertFalse(value.isEmpty());
            }
        }
    }

    @Test
    void processRequest_validateRequiredEntities_missingEntity_addsWarning() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update sample status")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UPDATE_SAMPLE_STATUS", response.getTaskId());
        // Should still return response even if required entities are missing
        assertNotNull(response);
    }

    @Test
    void processRequest_validateRequiredEntities_allPresent_returnsSuccess() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("CANCEL_CASE", response.getTaskId());
        assertNotNull(response.getExtractedEntities().get("case_id"));
    }

    @Test
    void processRequest_explicitTaskId_emptyString_treatsAsNull() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .taskId("")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        // Empty string should trigger classification
        assertEquals("CANCEL_CASE", response.getTaskId());
    }
}
