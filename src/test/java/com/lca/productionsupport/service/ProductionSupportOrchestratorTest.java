package com.lca.productionsupport.service;

import com.lca.productionsupport.model.OperationalRequest;
import com.lca.productionsupport.model.OperationalResponse;
import com.lca.productionsupport.model.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ProductionSupportOrchestratorTest {

    private ProductionSupportOrchestrator orchestrator;
    private PatternClassifier patternClassifier;
    private RunbookParser runbookParser;

    @BeforeEach
    void setUp() {
        patternClassifier = new PatternClassifier();
        runbookParser = new RunbookParser();
        orchestrator = new ProductionSupportOrchestrator(patternClassifier, runbookParser);
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
        assertTrue(response.getWarnings().contains("Case cancellation is a critical operation. Please review pre-checks carefully."));
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
        assertTrue(response.getWarnings().contains("No case ID found in query. You'll need to provide it manually."));
        assertTrue(response.getWarnings().contains("Case cancellation is a critical operation. Please review pre-checks carefully."));
    }

    @Test
    void processRequest_updateStatus_withCaseIdAndStatus() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update status to pending 2025123P6732")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UPDATE_CASE_STATUS", response.getTaskId());
        assertEquals("Update Case Status", response.getTaskName());
        assertEquals("ap-services", response.getDownstreamService());
        assertEquals("2025123P6732", response.getExtractedEntities().get("case_id"));
        assertEquals("pending", response.getExtractedEntities().get("status"));
        assertNotNull(response.getSteps());
    }

    @Test
    void processRequest_updateStatus_withoutStatus() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update status for case 2025123P6732")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UPDATE_CASE_STATUS", response.getTaskId());
        assertTrue(response.getWarnings().contains("No target status found in query. You'll need to provide it manually."));
    }

    @Test
    void processRequest_updateStatus_withoutCaseId() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update status to pending")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UPDATE_CASE_STATUS", response.getTaskId());
        assertTrue(response.getWarnings().contains("No case ID found in query. You'll need to provide it manually."));
    }

    @Test
    void processRequest_updateStatus_withoutBoth() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update status")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UPDATE_CASE_STATUS", response.getTaskId());
        assertTrue(response.getWarnings().contains("No case ID found in query. You'll need to provide it manually."));
        assertTrue(response.getWarnings().contains("No target status found in query. You'll need to provide it manually."));
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
            .query("case 2025123P6732 pending")
            .taskId("UPDATE_CASE_STATUS")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals("UPDATE_CASE_STATUS", response.getTaskId());
        assertEquals("2025123P6732", response.getExtractedEntities().get("case_id"));
        assertEquals("pending", response.getExtractedEntities().get("status"));
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
            .taskId("cancel_case")
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
        // Some groups may be null if empty (per groupStepsByType logic)
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
        
        // UPDATE_CASE_STATUS should have at least procedure steps
        assertNotNull(stepGroups.getProcedure());
        assertTrue(stepGroups.getProcedure().size() > 0);
    }

    // ========== Get Available Tasks Tests ==========

    @Test
    void getAvailableTasks_returnsAllTasksExceptUnknown() {
        List<Map<String, String>> tasks = orchestrator.getAvailableTasks();

        assertNotNull(tasks);
        assertTrue(tasks.size() > 0);
        
        // Should have at least CANCEL_CASE and UPDATE_CASE_STATUS
        assertTrue(tasks.stream().anyMatch(t -> "CANCEL_CASE".equals(t.get("taskId"))));
        assertTrue(tasks.stream().anyMatch(t -> "UPDATE_CASE_STATUS".equals(t.get("taskId"))));
        
        // Should NOT have UNKNOWN
        assertFalse(tasks.stream().anyMatch(t -> "UNKNOWN".equals(t.get("taskId"))));
    }

    @Test
    void getAvailableTasks_hasRequiredFields() {
        List<Map<String, String>> tasks = orchestrator.getAvailableTasks();

        for (Map<String, String> task : tasks) {
            assertNotNull(task.get("taskId"));
            assertNotNull(task.get("taskName"));
            assertNotNull(task.get("description"));
        }
    }

    @Test
    void getAvailableTasks_cancelCaseHasCorrectFields() {
        List<Map<String, String>> tasks = orchestrator.getAvailableTasks();

        Optional<Map<String, String>> cancelTask = tasks.stream()
            .filter(t -> "CANCEL_CASE".equals(t.get("taskId")))
            .findFirst();

        assertTrue(cancelTask.isPresent());
        assertEquals("CANCEL_CASE", cancelTask.get().get("taskId"));
        assertEquals("Cancel Case", cancelTask.get().get("taskName"));
        assertTrue(cancelTask.get().get("description").contains("Cancel a pathology case"));
    }

    @Test
    void getAvailableTasks_updateCaseStatusHasCorrectFields() {
        List<Map<String, String>> tasks = orchestrator.getAvailableTasks();

        Optional<Map<String, String>> updateTask = tasks.stream()
            .filter(t -> "UPDATE_CASE_STATUS".equals(t.get("taskId")))
            .findFirst();

        assertTrue(updateTask.isPresent());
        assertEquals("UPDATE_CASE_STATUS", updateTask.get().get("taskId"));
        assertEquals("Update Case Status", updateTask.get().get("taskName"));
        assertTrue(updateTask.get().get("description").contains("Update case workflow status"));
    }

    // ========== Warning Tests ==========

    @Test
    void processRequest_warnings_cancelCaseWithCaseId() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals(1, response.getWarnings().size());
        assertTrue(response.getWarnings().contains("Case cancellation is a critical operation. Please review pre-checks carefully."));
    }

    @Test
    void processRequest_warnings_cancelCaseWithoutCaseId() {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals(2, response.getWarnings().size());
        assertTrue(response.getWarnings().contains("No case ID found in query. You'll need to provide it manually."));
        assertTrue(response.getWarnings().contains("Case cancellation is a critical operation. Please review pre-checks carefully."));
    }

    @Test
    void processRequest_warnings_updateStatusComplete() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update status to pending 2025123P6732")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertEquals(0, response.getWarnings().size());
    }

    @Test
    void processRequest_warnings_unknownTask() {
        OperationalRequest request = OperationalRequest.builder()
            .query("hello world")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        assertTrue(response.getWarnings().contains("No case ID found in query. You'll need to provide it manually."));
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
        assertTrue(response.getWarnings().contains("No case ID found in query. You'll need to provide it manually."));
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

    // ========== Integration Tests (with real dependencies) ==========

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
        
        // Verify warning
        assertTrue(response.getWarnings().contains("Case cancellation is a critical operation. Please review pre-checks carefully."));
    }

    @Test
    void processRequest_endToEnd_updateStatus() {
        OperationalRequest request = OperationalRequest.builder()
            .query("update case status to pending for 2025123P6732")
            .userId("pathologist@example.com")
            .downstreamService("ap-services")
            .build();

        OperationalResponse response = orchestrator.processRequest(request);

        // Verify complete response
        assertEquals("UPDATE_CASE_STATUS", response.getTaskId());
        assertEquals("Update Case Status", response.getTaskName());
        assertEquals("ap-services", response.getDownstreamService());
        assertEquals("2025123P6732", response.getExtractedEntities().get("case_id"));
        assertEquals("pending", response.getExtractedEntities().get("status"));
        
        // Verify steps are populated
        assertNotNull(response.getSteps());
        assertTrue(response.getSteps().getPrechecks().size() > 0);
        assertTrue(response.getSteps().getProcedure().size() > 0);
        
        // No warnings for complete input
        assertEquals(0, response.getWarnings().size());
    }
}

