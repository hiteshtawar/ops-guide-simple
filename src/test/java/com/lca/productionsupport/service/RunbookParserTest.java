package com.lca.productionsupport.service;

import com.lca.productionsupport.model.OperationalResponse.RunbookStep;
import com.lca.productionsupport.model.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RunbookParserTest {

    private RunbookParser runbookParser;

    @BeforeEach
    void setUp() {
        runbookParser = new RunbookParser();
    }

    // ========== Get Steps Tests ==========

    @Test
    void getSteps_cancelCase_returnsAllSteps() {
        List<RunbookStep> steps = runbookParser.getSteps("CANCEL_CASE", null);

        assertNotNull(steps);
        assertTrue(steps.size() > 0);
        assertEquals(5, steps.size()); // CANCEL_CASE has 5 steps: HEADER_CHECK, LOCAL_MESSAGE, POST, GET, GET
    }

    @Test
    void getSteps_updateCaseStatus_returnsAllSteps() {
        List<RunbookStep> steps = runbookParser.getSteps("UPDATE_CASE_STATUS", null);

        assertNotNull(steps);
        assertTrue(steps.size() > 0);
        assertEquals(9, steps.size()); // UPDATE_CASE_STATUS has 9 steps based on runbook
    }

    @Test
    void getSteps_unknownTask_returnsEmptyList() {
        List<RunbookStep> steps = runbookParser.getSteps("UNKNOWN", null);

        assertNotNull(steps);
        assertTrue(steps.isEmpty());
    }

    @Test
    void getSteps_invalidTaskId_returnsEmptyList() {
        List<RunbookStep> steps = runbookParser.getSteps("INVALID_TASK", null);

        assertNotNull(steps);
        assertTrue(steps.isEmpty());
    }

    @Test
    void getSteps_nullTaskId_returnsEmptyList() {
        List<RunbookStep> steps = runbookParser.getSteps(null, null);

        assertNotNull(steps);
        assertTrue(steps.isEmpty());
    }

    // ========== Filter by Step Type Tests ==========

    @Test
    void getSteps_filterByPrecheck() {
        List<RunbookStep> steps = runbookParser.getSteps("CANCEL_CASE", "precheck");

        assertNotNull(steps);
        for (RunbookStep step : steps) {
            assertEquals("precheck", step.getStepType().toLowerCase());
        }
    }

    @Test
    void getSteps_filterByProcedure() {
        List<RunbookStep> steps = runbookParser.getSteps("CANCEL_CASE", "procedure");

        assertNotNull(steps);
        for (RunbookStep step : steps) {
            assertEquals("procedure", step.getStepType().toLowerCase());
        }
    }

    @Test
    void getSteps_filterByPostcheck() {
        List<RunbookStep> steps = runbookParser.getSteps("CANCEL_CASE", "postcheck");

        assertNotNull(steps);
        for (RunbookStep step : steps) {
            assertEquals("postcheck", step.getStepType().toLowerCase());
        }
    }

    @Test
    void getSteps_filterByRollback() {
        List<RunbookStep> steps = runbookParser.getSteps("CANCEL_CASE", "rollback");

        assertNotNull(steps);
        for (RunbookStep step : steps) {
            assertEquals("rollback", step.getStepType().toLowerCase());
        }
    }

    @Test
    void getSteps_filterCaseInsensitive() {
        List<RunbookStep> prechecksLower = runbookParser.getSteps("CANCEL_CASE", "precheck");
        List<RunbookStep> prechecksUpper = runbookParser.getSteps("CANCEL_CASE", "PRECHECK");
        List<RunbookStep> prechecksMixed = runbookParser.getSteps("CANCEL_CASE", "PreCheck");

        assertEquals(prechecksLower.size(), prechecksUpper.size());
        assertEquals(prechecksLower.size(), prechecksMixed.size());
    }

    @Test
    void getSteps_filterByEmptyString_returnsAllSteps() {
        List<RunbookStep> allSteps = runbookParser.getSteps("CANCEL_CASE", null);
        List<RunbookStep> stepsWithEmptyFilter = runbookParser.getSteps("CANCEL_CASE", "");

        assertEquals(allSteps.size(), stepsWithEmptyFilter.size());
    }

    @Test
    void getSteps_filterByInvalidType_returnsEmpty() {
        List<RunbookStep> steps = runbookParser.getSteps("CANCEL_CASE", "invalid-type");

        assertNotNull(steps);
        assertTrue(steps.isEmpty());
    }

    // ========== Get Step by Number Tests ==========

    @Test
    void getStep_validStepNumber() {
        RunbookStep step = runbookParser.getStep("CANCEL_CASE", 1);

        assertNotNull(step);
        assertEquals(1, step.getStepNumber());
        assertNotNull(step.getDescription());
        assertNotNull(step.getStepType());
    }

    @Test
    void getStep_lastStep() {
        RunbookStep step = runbookParser.getStep("CANCEL_CASE", 5);

        assertNotNull(step);
        assertEquals(5, step.getStepNumber());
    }

    @Test
    void getStep_invalidTaskId_returnsNull() {
        RunbookStep step = runbookParser.getStep("INVALID_TASK", 1);

        assertNull(step);
    }

    @Test
    void getStep_stepNumberTooLow_returnsNull() {
        RunbookStep step = runbookParser.getStep("CANCEL_CASE", 0);

        assertNull(step);
    }

    @Test
    void getStep_stepNumberNegative_returnsNull() {
        RunbookStep step = runbookParser.getStep("CANCEL_CASE", -1);

        assertNull(step);
    }

    @Test
    void getStep_stepNumberTooHigh_returnsNull() {
        RunbookStep step = runbookParser.getStep("CANCEL_CASE", 999);

        assertNull(step);
    }

    @Test
    void getStep_nullTaskId_returnsNull() {
        RunbookStep step = runbookParser.getStep(null, 1);

        assertNull(step);
    }

    // ========== Step Content Validation Tests ==========

    @Test
    void getSteps_allStepsHaveRequiredFields() {
        List<RunbookStep> steps = runbookParser.getSteps("CANCEL_CASE", null);

        for (RunbookStep step : steps) {
            assertNotNull(step.getStepNumber(), "Step number should not be null");
            assertNotNull(step.getDescription(), "Description should not be null");
            assertNotNull(step.getStepType(), "Step type should not be null");
            assertNotNull(step.getAutoExecutable(), "Auto executable should not be null");
        }
    }

    @Test
    void getSteps_stepNumbersAreSequential() {
        List<RunbookStep> steps = runbookParser.getSteps("CANCEL_CASE", null);

        for (int i = 0; i < steps.size(); i++) {
            assertEquals(i + 1, steps.get(i).getStepNumber());
        }
    }

    @Test
    void getSteps_updateCaseStatus_allStepsHaveRequiredFields() {
        List<RunbookStep> steps = runbookParser.getSteps("UPDATE_CASE_STATUS", null);

        for (RunbookStep step : steps) {
            assertNotNull(step.getStepNumber());
            assertNotNull(step.getDescription());
            assertNotNull(step.getStepType());
            assertNotNull(step.getAutoExecutable());
        }
    }

    @Test
    void getSteps_stepTypesAreValid() {
        List<RunbookStep> steps = runbookParser.getSteps("CANCEL_CASE", null);

        for (RunbookStep step : steps) {
            String stepType = step.getStepType().toLowerCase();
            assertTrue(
                stepType.equals("precheck") || 
                stepType.equals("procedure") || 
                stepType.equals("postcheck") || 
                stepType.equals("rollback"),
                "Step type should be precheck, procedure, postcheck, or rollback"
            );
        }
    }

    // ========== Cache Tests ==========

    @Test
    void getSteps_cachedOnMultipleCalls() {
        List<RunbookStep> steps1 = runbookParser.getSteps("CANCEL_CASE", null);
        List<RunbookStep> steps2 = runbookParser.getSteps("CANCEL_CASE", null);

        // Should return same cached instance
        assertEquals(steps1.size(), steps2.size());
    }

    @Test
    void getSteps_separateTasksHaveDifferentSteps() {
        List<RunbookStep> cancelSteps = runbookParser.getSteps("CANCEL_CASE", null);
        List<RunbookStep> updateSteps = runbookParser.getSteps("UPDATE_CASE_STATUS", null);

        // Different runbooks should have different step counts
        assertNotEquals(cancelSteps.size(), updateSteps.size());
    }
}

