package com.lca.productionsupport.service;

import com.lca.productionsupport.model.OperationalResponse;
import com.lca.productionsupport.model.StepMethod;
import com.lca.productionsupport.model.UseCaseDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RunbookAdapterTest {

    private RunbookAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RunbookAdapter();
    }

    @Test
    void toOperationalResponse_basicConversion() {
        UseCaseDefinition useCase = createBasicUseCase();
        Map<String, String> entities = Map.of("case_id", "2025123P6732");
        
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertEquals("CANCEL_CASE", response.getTaskId());
        assertEquals("Cancel Case", response.getTaskName());
        assertEquals("ap-services", response.getDownstreamService());
        assertEquals("2025123P6732", response.getExtractedEntities().get("case_id"));
        assertNotNull(response.getSteps());
    }

    @Test
    void toOperationalResponse_defaultDownstreamService() {
        UseCaseDefinition useCase = createBasicUseCase();
        useCase.getUseCase().setDownstreamService(null);
        Map<String, String> entities = new HashMap<>();
        
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertEquals("ap-services", response.getDownstreamService());
    }

    @Test
    void toOperationalResponse_customDownstreamService() {
        UseCaseDefinition useCase = createBasicUseCase();
        useCase.getUseCase().setDownstreamService("custom-service");
        Map<String, String> entities = new HashMap<>();
        
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertEquals("custom-service", response.getDownstreamService());
    }

    @Test
    void toOperationalResponse_groupsStepsByType_prechecks() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition precheckStep = new UseCaseDefinition.StepDefinition();
        precheckStep.setStepNumber(1);
        precheckStep.setStepType("prechecks");
        precheckStep.setMethod("HEADER_CHECK");
        precheckStep.setPath("Role-Name");
        precheckStep.setDescription("Verify role");
        useCase.getExecution().getSteps().add(precheckStep);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertNotNull(response.getSteps().getPrechecks());
        assertEquals(1, response.getSteps().getPrechecks().size());
        assertEquals("prechecks", response.getSteps().getPrechecks().get(0).getStepType());
    }

    @Test
    void toOperationalResponse_groupsStepsByType_procedure() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition procedureStep = new UseCaseDefinition.StepDefinition();
        procedureStep.setStepNumber(1);
        procedureStep.setStepType("procedure");
        procedureStep.setMethod("DELETE");
        procedureStep.setPath("/api/cases/{case_id}");
        procedureStep.setDescription("Cancel case");
        useCase.getExecution().getSteps().add(procedureStep);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertNotNull(response.getSteps().getProcedure());
        assertTrue(response.getSteps().getProcedure().size() > 0);
    }

    @Test
    void toOperationalResponse_groupsStepsByType_postchecks() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition postcheckStep = new UseCaseDefinition.StepDefinition();
        postcheckStep.setStepNumber(3);
        postcheckStep.setStepType("postchecks");
        postcheckStep.setMethod("GET");
        postcheckStep.setPath("/api/cases/{case_id}");
        postcheckStep.setDescription("Verify cancellation");
        useCase.getExecution().getSteps().add(postcheckStep);
        
        Map<String, String> entities = Map.of("case_id", "2025123P6732");
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertNotNull(response.getSteps().getPostchecks());
        assertEquals(1, response.getSteps().getPostchecks().size());
    }

    @Test
    void toOperationalResponse_groupsStepsByType_rollback() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition rollbackStep = new UseCaseDefinition.StepDefinition();
        rollbackStep.setStepNumber(4);
        rollbackStep.setStepType("rollback");
        rollbackStep.setMethod("POST");
        rollbackStep.setPath("/api/cases/{case_id}/restore");
        rollbackStep.setDescription("Restore case");
        useCase.getExecution().getSteps().add(rollbackStep);
        
        Map<String, String> entities = Map.of("case_id", "2025123P6732");
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertNotNull(response.getSteps().getRollback());
        assertEquals(1, response.getSteps().getRollback().size());
    }

    @Test
    void toOperationalResponse_defaultStepTypeToProcedure() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition step = new UseCaseDefinition.StepDefinition();
        step.setStepNumber(5);
        step.setStepType(null);
        step.setMethod("GET");
        step.setPath("/api/test");
        useCase.getExecution().getSteps().add(step);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertTrue(response.getSteps().getProcedure().size() > 0);
    }

    @Test
    void toOperationalResponse_unknownStepType_defaultsToProcedure() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition step = new UseCaseDefinition.StepDefinition();
        step.setStepNumber(5);
        step.setStepType("unknown_type");
        step.setMethod("GET");
        step.setPath("/api/test");
        useCase.getExecution().getSteps().add(step);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertTrue(response.getSteps().getProcedure().size() > 0);
    }

    @Test
    void toOperationalResponse_localMessageStep() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition localMessageStep = new UseCaseDefinition.StepDefinition();
        localMessageStep.setStepNumber(1);
        localMessageStep.setMethod("LOCAL_MESSAGE");
        localMessageStep.setLocalMessage("This is a local message");
        localMessageStep.setStepType("prechecks");
        useCase.getExecution().getSteps().add(0, localMessageStep);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getPrechecks().get(0);
        assertEquals(StepMethod.LOCAL_MESSAGE, step.getMethod());
        assertEquals("This is a local message", step.getDescription());
        assertEquals("This is a local message", step.getRequestBody());
        assertEquals("This is a local message", step.getExpectedResponse());
        assertTrue(step.getAutoExecutable());
        assertNull(step.getPath());
    }

    @Test
    void toOperationalResponse_localMessageStep_withPlaceholder() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition localMessageStep = new UseCaseDefinition.StepDefinition();
        localMessageStep.setStepNumber(1);
        localMessageStep.setMethod("LOCAL_MESSAGE");
        localMessageStep.setLocalMessage("Processing case {case_id}");
        localMessageStep.setStepType("prechecks");
        useCase.getExecution().getSteps().add(0, localMessageStep);
        
        Map<String, String> entities = Map.of("case_id", "2025123P6732");
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getPrechecks().get(0);
        assertEquals("Processing case 2025123P6732", step.getDescription());
    }

    @Test
    void toOperationalResponse_localMessageStep_fallsBackToDescription() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition localMessageStep = new UseCaseDefinition.StepDefinition();
        localMessageStep.setStepNumber(1);
        localMessageStep.setMethod("LOCAL_MESSAGE");
        localMessageStep.setLocalMessage(null);
        localMessageStep.setDescription("Fallback description");
        localMessageStep.setStepType("prechecks");
        useCase.getExecution().getSteps().add(0, localMessageStep);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getPrechecks().get(0);
        assertEquals("Fallback description", step.getDescription());
    }

    @Test
    void toOperationalResponse_headerCheckStep() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition headerCheckStep = new UseCaseDefinition.StepDefinition();
        headerCheckStep.setStepNumber(1);
        headerCheckStep.setMethod("HEADER_CHECK");
        headerCheckStep.setPath("Role-Name");
        headerCheckStep.setExpectedResponse("pathologist");
        headerCheckStep.setStepType("prechecks");
        useCase.getExecution().getSteps().add(0, headerCheckStep);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getPrechecks().get(0);
        assertEquals(StepMethod.HEADER_CHECK, step.getMethod());
        assertEquals("Role-Name", step.getPath());
        assertEquals("pathologist", step.getExpectedResponse());
        assertTrue(step.getAutoExecutable());
        assertNull(step.getRequestBody());
    }

    @Test
    void toOperationalResponse_headerCheckStep_withPlaceholder() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition headerCheckStep = new UseCaseDefinition.StepDefinition();
        headerCheckStep.setStepNumber(1);
        headerCheckStep.setMethod("HEADER_CHECK");
        headerCheckStep.setPath("Role-Name");
        headerCheckStep.setExpectedResponse("{role}");
        headerCheckStep.setStepType("prechecks");
        useCase.getExecution().getSteps().add(0, headerCheckStep);
        
        Map<String, String> entities = Map.of("role", "pathologist");
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getPrechecks().get(0);
        assertEquals("pathologist", step.getExpectedResponse());
    }

    @Test
    void toOperationalResponse_headerCheckStep_defaultDescription() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition headerCheckStep = new UseCaseDefinition.StepDefinition();
        headerCheckStep.setStepNumber(1);
        headerCheckStep.setMethod("HEADER_CHECK");
        headerCheckStep.setPath("Role-Name");
        headerCheckStep.setDescription(null);
        headerCheckStep.setStepType("prechecks");
        useCase.getExecution().getSteps().add(0, headerCheckStep);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getPrechecks().get(0);
        assertEquals("Verify Role-Name header", step.getDescription());
    }

    @Test
    void toOperationalResponse_httpStep_withPlaceholders() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition httpStep = new UseCaseDefinition.StepDefinition();
        httpStep.setStepNumber(2);
        httpStep.setMethod("DELETE");
        httpStep.setPath("/api/cases/{case_id}");
        httpStep.setDescription("Cancel case {case_id}");
        httpStep.setStepType("procedure");
        useCase.getExecution().getSteps().add(httpStep);
        
        Map<String, String> entities = Map.of("case_id", "2025123P6732");
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getProcedure().get(0);
        assertEquals(StepMethod.DELETE, step.getMethod());
        assertEquals("/api/cases/2025123P6732", step.getPath());
        assertEquals("Cancel case 2025123P6732", step.getDescription());
    }

    @Test
    void toOperationalResponse_httpStep_withRequestBody() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition httpStep = new UseCaseDefinition.StepDefinition();
        httpStep.setStepNumber(2);
        httpStep.setMethod("POST");
        httpStep.setPath("/api/cases");
        httpStep.setDescription("Create case");
        Map<String, Object> body = new HashMap<>();
        body.put("caseId", "{case_id}");
        body.put("status", "pending");
        httpStep.setBody(body);
        httpStep.setStepType("procedure");
        useCase.getExecution().getSteps().add(httpStep);
        
        Map<String, String> entities = Map.of("case_id", "2025123P6732");
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getProcedure().get(0);
        assertNotNull(step.getRequestBody());
        assertTrue(step.getRequestBody().contains("2025123P6732"));
        assertTrue(step.getRequestBody().contains("pending"));
    }

    @Test
    void toOperationalResponse_httpStep_withNestedRequestBody() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition httpStep = new UseCaseDefinition.StepDefinition();
        httpStep.setStepNumber(2);
        httpStep.setMethod("POST");
        httpStep.setPath("/api/cases");
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> nested = new HashMap<>();
        nested.put("id", "{case_id}");
        body.put("data", nested);
        httpStep.setBody(body);
        httpStep.setStepType("procedure");
        useCase.getExecution().getSteps().add(httpStep);
        
        Map<String, String> entities = Map.of("case_id", "2025123P6732");
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getProcedure().get(0);
        assertNotNull(step.getRequestBody());
        assertTrue(step.getRequestBody().contains("2025123P6732"));
    }

    @Test
    void toOperationalResponse_httpStep_withAutoExecutable() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition httpStep = new UseCaseDefinition.StepDefinition();
        httpStep.setStepNumber(2);
        httpStep.setMethod("GET");
        httpStep.setPath("/api/test");
        httpStep.setAutoExecutable(false);
        httpStep.setStepType("procedure");
        useCase.getExecution().getSteps().add(httpStep);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getProcedure().get(0);
        assertFalse(step.getAutoExecutable());
    }

    @Test
    void toOperationalResponse_includesWarnings() {
        UseCaseDefinition useCase = createBasicUseCase();
        useCase.setWarnings(List.of("Warning 1", "Warning 2"));
        Map<String, String> entities = new HashMap<>();
        
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertNotNull(response.getWarnings());
        assertEquals(2, response.getWarnings().size());
        assertEquals("Warning 1", response.getWarnings().get(0));
    }

    @Test
    void toOperationalResponse_withNullEntities() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition step = new UseCaseDefinition.StepDefinition();
        step.setStepNumber(2);
        step.setMethod("GET");
        step.setPath("/api/cases/{case_id}");
        step.setStepType("procedure");
        useCase.getExecution().getSteps().add(step);
        
        OperationalResponse response = adapter.toOperationalResponse(useCase, null);
        
        assertNotNull(response);
        OperationalResponse.RunbookStep stepResult = response.getSteps().getProcedure().get(0);
        assertEquals("/api/cases/{case_id}", stepResult.getPath()); // Placeholder not replaced
    }

    @Test
    void toOperationalResponse_withEmptyEntities() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition step = new UseCaseDefinition.StepDefinition();
        step.setStepNumber(2);
        step.setMethod("GET");
        step.setPath("/api/cases/{case_id}");
        step.setStepType("procedure");
        useCase.getExecution().getSteps().add(step);
        
        OperationalResponse response = adapter.toOperationalResponse(useCase, new HashMap<>());
        
        assertNotNull(response);
        OperationalResponse.RunbookStep stepResult = response.getSteps().getProcedure().get(0);
        assertEquals("/api/cases/{case_id}", stepResult.getPath()); // Placeholder not replaced
    }

    @Test
    void toOperationalResponse_stepTypeCaseInsensitive() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition step1 = new UseCaseDefinition.StepDefinition();
        step1.setStepNumber(1);
        step1.setStepType("PRECHECKS");
        step1.setMethod("GET");
        step1.setPath("/api/test");
        useCase.getExecution().getSteps().add(step1);
        
        UseCaseDefinition.StepDefinition step2 = new UseCaseDefinition.StepDefinition();
        step2.setStepNumber(2);
        step2.setStepType("PostCheck");
        step2.setMethod("GET");
        step2.setPath("/api/test");
        useCase.getExecution().getSteps().add(step2);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertEquals(1, response.getSteps().getPrechecks().size());
        assertEquals(1, response.getSteps().getPostchecks().size());
    }

    @Test
    void toOperationalResponse_precheckAlias() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition step = new UseCaseDefinition.StepDefinition();
        step.setStepNumber(1);
        step.setStepType("precheck");
        step.setMethod("GET");
        step.setPath("/api/test");
        useCase.getExecution().getSteps().add(step);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertEquals(1, response.getSteps().getPrechecks().size());
    }

    @Test
    void toOperationalResponse_postcheckAlias() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition step = new UseCaseDefinition.StepDefinition();
        step.setStepNumber(1);
        step.setStepType("postcheck");
        step.setMethod("GET");
        step.setPath("/api/test");
        useCase.getExecution().getSteps().add(step);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertEquals(1, response.getSteps().getPostchecks().size());
    }

    @Test
    void toOperationalResponse_httpStep_withNullDescription() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition httpStep = new UseCaseDefinition.StepDefinition();
        httpStep.setStepNumber(2);
        httpStep.setMethod("GET");
        httpStep.setPath("/api/test");
        httpStep.setDescription(null);
        httpStep.setStepType("procedure");
        useCase.getExecution().getSteps().add(httpStep);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getProcedure().get(0);
        assertNull(step.getDescription());
    }

    @Test
    void toOperationalResponse_httpStep_withNullExpectedResponse() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition httpStep = new UseCaseDefinition.StepDefinition();
        httpStep.setStepNumber(2);
        httpStep.setMethod("GET");
        httpStep.setPath("/api/test");
        httpStep.setExpectedResponse(null);
        httpStep.setStepType("procedure");
        useCase.getExecution().getSteps().add(httpStep);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getProcedure().get(0);
        assertNull(step.getExpectedResponse());
    }

    @Test
    void toOperationalResponse_httpStep_withEmptyRequestBody() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition httpStep = new UseCaseDefinition.StepDefinition();
        httpStep.setStepNumber(2);
        httpStep.setMethod("POST");
        httpStep.setPath("/api/test");
        httpStep.setBody(new HashMap<>());
        httpStep.setStepType("procedure");
        useCase.getExecution().getSteps().add(httpStep);
        
        Map<String, String> entities = new HashMap<>();
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getProcedure().get(0);
        assertNull(step.getRequestBody());
    }

    @Test
    void toOperationalResponse_replacePlaceholders_withNullTemplate() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition step = new UseCaseDefinition.StepDefinition();
        step.setStepNumber(2);
        step.setMethod("GET");
        step.setPath(null);
        step.setDescription(null);
        step.setStepType("procedure");
        useCase.getExecution().getSteps().add(step);
        
        Map<String, String> entities = Map.of("case_id", "2025123P6732");
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep stepResult = response.getSteps().getProcedure().get(0);
        assertNull(stepResult.getPath());
    }

    @Test
    void toOperationalResponse_replacePlaceholders_withNoMatchingPlaceholders() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition step = new UseCaseDefinition.StepDefinition();
        step.setStepNumber(2);
        step.setMethod("GET");
        step.setPath("/api/test");
        step.setStepType("procedure");
        useCase.getExecution().getSteps().add(step);
        
        Map<String, String> entities = Map.of("other_id", "123");
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep stepResult = response.getSteps().getProcedure().get(0);
        assertEquals("/api/test", stepResult.getPath());
    }

    @Test
    void toOperationalResponse_withNullWarnings_handlesGracefully() {
        UseCaseDefinition useCase = createBasicUseCase();
        useCase.setWarnings(null);
        Map<String, String> entities = new HashMap<>();
        
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertNull(response.getWarnings());
    }

    @Test
    void toOperationalResponse_withEmptyWarnings_handlesGracefully() {
        UseCaseDefinition useCase = createBasicUseCase();
        useCase.setWarnings(List.of());
        Map<String, String> entities = new HashMap<>();
        
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        assertNotNull(response.getWarnings());
        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    void toOperationalResponse_replacePlaceholdersInMap_withNestedNullValues() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition httpStep = new UseCaseDefinition.StepDefinition();
        httpStep.setStepNumber(2);
        httpStep.setMethod("POST");
        httpStep.setPath("/api/test");
        Map<String, Object> body = new HashMap<>();
        body.put("key1", "{barcode}");
        body.put("key2", null);
        httpStep.setBody(body);
        httpStep.setStepType("procedure");
        useCase.getExecution().getSteps().add(httpStep);
        
        Map<String, String> entities = Map.of("barcode", "BC123456");
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep step = response.getSteps().getProcedure().get(0);
        assertNotNull(step.getRequestBody());
        assertTrue(step.getRequestBody().contains("BC123456"));
    }

    @Test
    void toOperationalResponse_replacePlaceholders_withMultiplePlaceholders() {
        UseCaseDefinition useCase = createBasicUseCase();
        UseCaseDefinition.StepDefinition step = new UseCaseDefinition.StepDefinition();
        step.setStepNumber(2);
        step.setMethod("GET");
        step.setPath("/api/{barcode}/status/{sampleStatus}");
        step.setDescription("Update {barcode} to {sampleStatus}");
        step.setStepType("procedure");
        useCase.getExecution().getSteps().add(step);
        
        Map<String, String> entities = Map.of("barcode", "BC123456", "sampleStatus", "Completed");
        OperationalResponse response = adapter.toOperationalResponse(useCase, entities);
        
        OperationalResponse.RunbookStep stepResult = response.getSteps().getProcedure().get(0);
        assertEquals("/api/BC123456/status/Completed", stepResult.getPath());
        assertEquals("Update BC123456 to Completed", stepResult.getDescription());
    }

    // Helper method to create a basic use case for testing
    private UseCaseDefinition createBasicUseCase() {
        UseCaseDefinition useCase = new UseCaseDefinition();
        
        UseCaseDefinition.UseCaseInfo useCaseInfo = new UseCaseDefinition.UseCaseInfo();
        useCaseInfo.setId("CANCEL_CASE");
        useCaseInfo.setName("Cancel Case");
        useCaseInfo.setDownstreamService("ap-services");
        useCase.setUseCase(useCaseInfo);
        
        UseCaseDefinition.ClassificationConfig classification = new UseCaseDefinition.ClassificationConfig();
        classification.setKeywords(List.of("cancel"));
        useCase.setClassification(classification);
        
        UseCaseDefinition.ExecutionConfig execution = new UseCaseDefinition.ExecutionConfig();
        execution.setSteps(new java.util.ArrayList<>());
        useCase.setExecution(execution);
        
        return useCase;
    }
}

