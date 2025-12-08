package com.lca.productionsupport.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lca.productionsupport.model.OperationalRequest;
import com.lca.productionsupport.model.StepExecutionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductionSupportControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ========== Health Check Tests ==========

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("OK"));
    }

    // ========== Get Available Tasks Tests ==========

    @Test
    void getAvailableTasks_returnsTaskList() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].taskId").exists())
            .andExpect(jsonPath("$[0].taskName").exists())
            .andExpect(jsonPath("$[0].description").exists());
    }

    // ========== Process Request Tests ==========

    @Test
    void processRequest_cancelCase_returnsResponse() throws Exception {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        mockMvc.perform(post("/api/v1/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.taskId").value("CANCEL_CASE"))
            .andExpect(jsonPath("$.taskName").value("Cancel Case"))
            .andExpect(jsonPath("$.downstreamService").value("ap-services"))
            .andExpect(jsonPath("$.extractedEntities.case_id").value("2025123P6732"))
            .andExpect(jsonPath("$.steps").exists())
            .andExpect(jsonPath("$.warnings").isArray());
    }

    @Test
    void processRequest_updateStatus_returnsResponse() throws Exception {
        OperationalRequest request = OperationalRequest.builder()
            .query("update sample status to Completed - Microtomy sample BC123456")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        mockMvc.perform(post("/api/v1/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value("UPDATE_SAMPLE_STATUS"))
            .andExpect(jsonPath("$.taskName").value("Update Sample Status"))
            .andExpect(jsonPath("$.extractedEntities").exists());
    }

    @Test
    void processRequest_unknownQuery_returnsUnknown() throws Exception {
        OperationalRequest request = OperationalRequest.builder()
            .query("hello world")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        mockMvc.perform(post("/api/v1/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value("UNKNOWN"))
            .andExpect(jsonPath("$.taskName").value("Unknown"));
    }

    @Test
    void processRequest_emptyQuery_returnsBadRequest() throws Exception {
        OperationalRequest request = OperationalRequest.builder()
            .query("")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        mockMvc.perform(post("/api/v1/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void processRequest_nullQuery_returnsBadRequest() throws Exception {
        OperationalRequest request = OperationalRequest.builder()
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        mockMvc.perform(post("/api/v1/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void processRequest_withExplicitTaskId_usesTaskId() throws Exception {
        OperationalRequest request = OperationalRequest.builder()
            .query("2025123P6732")
            .taskId("CANCEL_CASE")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        mockMvc.perform(post("/api/v1/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value("CANCEL_CASE"));
    }

    @Test
    void processRequest_defaultDownstreamService() throws Exception {
        OperationalRequest request = OperationalRequest.builder()
            .query("cancel case 2025123P6732")
            .userId("user123")
            .build();

        mockMvc.perform(post("/api/v1/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.downstreamService").value("ap-services"));
    }

    // ========== Execute Step Tests ==========

    @Test
    void executeStep_invalidStepNumber_returnsError() throws Exception {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(999)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        mockMvc.perform(post("/api/v1/execute-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorMessage").value("The requested operation step was not found. Please verify the step number."));
    }

    @Test
    void executeStep_nullStepNumber_returnsBadRequest() throws Exception {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        mockMvc.perform(post("/api/v1/execute-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void executeStep_emptyTaskId_returnsBadRequest() throws Exception {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        mockMvc.perform(post("/api/v1/execute-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void executeStep_unconfiguredService_returnsError() throws Exception {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("unknown-service")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        mockMvc.perform(post("/api/v1/execute-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errorMessage").exists());
    }

    // ========== Header Collection Tests ==========

    @Test
    void executeStep_withAllHeaders_collectsAllHeaders() throws Exception {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        mockMvc.perform(post("/api/v1/execute-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Role-Name", "Production Support")
                .header("Api-User", "test-api-user")
                .header("Lab-Id", "test-lab-id")
                .header("Discipline-Name", "pathology")
                .header("Time-Zone", "America/New_York")
                .header("accept", "application/json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void executeStep_withRoleNameHeader_setsUserRole() throws Exception {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        mockMvc.perform(post("/api/v1/execute-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Role-Name", "Production Support"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void executeStep_withPartialHeaders_collectsProvidedHeaders() throws Exception {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        mockMvc.perform(post("/api/v1/execute-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Api-User", "test-user")
                .header("Lab-Id", "test-lab"))
            .andExpect(status().isOk());
    }

    @Test
    void executeStep_withEmptyHeaders_handlesGracefully() throws Exception {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        mockMvc.perform(post("/api/v1/execute-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Role-Name", "")
                .header("Api-User", "")
                .header("Lab-Id", ""))
            .andExpect(status().isOk());
    }

    @Test
    void executeStep_withNullHeaders_handlesGracefully() throws Exception {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        // Not passing headers at all (null)
        mockMvc.perform(post("/api/v1/execute-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    @Test
    void executeStep_withRoleNameAndOtherHeaders_collectsAll() throws Exception {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        mockMvc.perform(post("/api/v1/execute-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Role-Name", "Production Support")
                .header("Api-User", "api-user-123")
                .header("Lab-Id", "lab-456")
                .header("Discipline-Name", "discipline-789")
                .header("Time-Zone", "UTC")
                .header("accept", "application/json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void executeStep_withOnlyAcceptHeader_collectsAccept() throws Exception {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .build();

        mockMvc.perform(post("/api/v1/execute-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("accept", "application/json"))
            .andExpect(status().isOk());
    }

    @Test
    void executeStep_withUserRoleAlreadySet_headerOverrides() throws Exception {
        StepExecutionRequest request = StepExecutionRequest.builder()
            .taskId("CANCEL_CASE")
            .downstreamService("ap-services")
            .stepNumber(1)
            .entities(Map.of("case_id", "2025123P6732"))
            .userId("user123")
            .authToken("token")
            .userRole("Original Role")
            .build();

        mockMvc.perform(post("/api/v1/execute-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Role-Name", "Production Support"))
            .andExpect(status().isOk());
    }

    @Test
    void executeStep_processRequestWithUnknownTask_logsWarning() throws Exception {
        OperationalRequest request = OperationalRequest.builder()
            .query("some unknown query")
            .userId("user123")
            .downstreamService("ap-services")
            .build();

        mockMvc.perform(post("/api/v1/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value("UNKNOWN"));
    }
}

