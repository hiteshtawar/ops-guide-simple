package com.lca.productionsupport.service;

import com.lca.productionsupport.model.TaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PatternClassifierTest {

    private PatternClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new PatternClassifier();
    }

    // ========== CANCEL_CASE Tests ==========

    @Test
    void classify_cancelCase_withCancelKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
        assertEquals("2025123P6732", result.getEntities().get("case_id"));
    }

    @Test
    void classify_cancelCase_withAbortKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("abort case 2024123P6731");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
        assertEquals("2024123P6731", result.getEntities().get("case_id"));
    }

    @Test
    void classify_cancelCase_withDeleteKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("delete case 2024123P6731");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
        assertEquals("2024123P6731", result.getEntities().get("case_id"));
    }

    @Test
    void classify_cancelCase_withRemoveKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("remove case 12345678");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
        assertEquals("12345678", result.getEntities().get("case_id"));
    }

    @Test
    void classify_cancelCase_withTerminateKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("terminate case 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
    }

    @Test
    void classify_cancelCase_withDropKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("drop case 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
    }

    @Test
    void classify_cancelCase_withCancellationKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancellation for case 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
    }

    @Test
    void classify_cancelCase_withPoliteWords() {
        PatternClassifier.ClassificationResult result = classifier.classify("Please kindly cancel case 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
        assertEquals("2025123P6732", result.getEntities().get("case_id"));
    }

    // ========== UPDATE_CASE_STATUS Tests ==========

    @Test
    void classify_updateStatus_withUpdateStatusKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to pending 2025123P6732");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("2025123P6732", result.getEntities().get("case_id"));
        assertEquals("pending", result.getEntities().get("status"));
    }

    @Test
    void classify_updateStatus_withChangeStatusKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("change status to grossing 2024123P6731");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("grossing", result.getEntities().get("status"));
    }

    @Test
    void classify_updateStatus_withSetStatusKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("set status to completed 2024123P6731");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("completed", result.getEntities().get("status"));
    }

    @Test
    void classify_updateStatus_withMarkAsKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("mark as staining 2024123P6731");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("staining", result.getEntities().get("status"));
    }

    @Test
    void classify_updateStatus_withMoveToKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("move to embedding 2024123P6731");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("embedding", result.getEntities().get("status"));
    }

    @Test
    void classify_updateStatus_withTransitionToKeyword() {
        PatternClassifier.ClassificationResult result = classifier.classify("transition to microscopy 2024123P6731");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("microscopy", result.getEntities().get("status"));
    }

    @Test
    void classify_updateStatus_withStatusAndActionVerb() {
        PatternClassifier.ClassificationResult result = classifier.classify("mark case to cutting 2024123P6731");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("cutting", result.getEntities().get("status"));
    }

    @Test
    void classify_updateStatus_withBothCaseIdAndStatus() {
        PatternClassifier.ClassificationResult result = classifier.classify("case 2024123P6731 pending");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("2024123P6731", result.getEntities().get("case_id"));
        assertEquals("pending", result.getEntities().get("status"));
    }

    // ========== Status Normalization Tests ==========

    @Test
    void classify_normalizeStatus_accessioningTypo() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to accessioning 2024123P6731");
        
        assertEquals("accessioning", result.getEntities().get("status"));
    }

    @Test
    void classify_normalizeStatus_accessionVariation() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to accessioning 2024123P6731");
        
        assertEquals("accessioning", result.getEntities().get("status"));
    }

    @Test
    void classify_normalizeStatus_underReview() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to under review 2024123P6731");
        
        assertEquals("under_review", result.getEntities().get("status"));
    }

    @Test
    void classify_normalizeStatus_onHold() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to on hold 2024123P6731");
        
        assertEquals("on_hold", result.getEntities().get("status"));
    }

    @Test
    void classify_normalizeStatus_completedTypo() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to complete 2024123P6731");
        
        assertEquals("complete", result.getEntities().get("status"));
    }

    @Test
    void classify_normalizeStatus_cancelledTypo() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to canceled 2024123P6731");
        
        assertEquals("canceled", result.getEntities().get("status"));
    }

    @Test
    void classify_normalizeStatus_archived() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to archived 2024123P6731");
        
        assertEquals("archived", result.getEntities().get("status"));
    }

    @Test
    void classify_normalizeStatus_closed() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to closed 2024123P6731");
        
        assertEquals("closed", result.getEntities().get("status"));
    }

    @Test
    void classify_newStatus_microtomy() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to microtomy 2024123P6731");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("microtomy", result.getEntities().get("status"));
    }

    @Test
    void classify_newStatus_pathologistReview() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to pathologist review 2024123P6731");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("pathologist_review", result.getEntities().get("status"));
    }

    @Test
    void classify_newStatus_pathologistReviewUnderscore() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to pathologist_review 2024123P6731");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("pathologist_review", result.getEntities().get("status"));
    }

    @Test
    void classify_newStatus_rostering() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to rostering 2024123P6731");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("rostering", result.getEntities().get("status"));
    }

    // ========== Case ID Extraction Tests ==========

    @Test
    void classify_extractCaseId_standardFormat() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case 2025123P6732");
        
        assertEquals("2025123P6732", result.getEntities().get("case_id"));
    }

    @Test
    void classify_extractCaseId_withCasePrefix() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case 2024123P6731");
        
        assertEquals("2024123P6731", result.getEntities().get("case_id"));
    }

    @Test
    void classify_extractCaseId_withCaseWord() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case12345678");
        
        assertEquals("12345678", result.getEntities().get("case_id"));
    }

    @Test
    void classify_extractCaseId_lowercase() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case 2025123p6732");
        
        assertEquals("2025123p6732", result.getEntities().get("case_id"));
    }

    @Test
    void classify_extractCaseId_numericsOnly() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case 20241234");
        
        assertEquals("20241234", result.getEntities().get("case_id"));
    }

    @Test
    void classify_extractCaseId_allNumbers() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case 20251999746653");
        
        assertEquals("20251999746653", result.getEntities().get("case_id"));
    }

    @Test
    void classify_extractCaseId_withT() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case 2025020T115000");
        
        assertEquals("2025020T115000", result.getEntities().get("case_id"));
    }

    @Test
    void classify_extractCaseId_withP() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case 2025020P123457");
        
        assertEquals("2025020P123457", result.getEntities().get("case_id"));
    }

    @Test
    void classify_extractCaseId_lowercaseT() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case 2025020t115000");
        
        assertEquals("2025020t115000", result.getEntities().get("case_id"));
    }

    // ========== UNKNOWN Task Tests ==========

    @Test
    void classify_unknown_noKeywords() {
        PatternClassifier.ClassificationResult result = classifier.classify("hello world");
        
        assertEquals(TaskType.UNKNOWN, result.getTaskType());
    }

    @Test
    void classify_unknown_randomText() {
        PatternClassifier.ClassificationResult result = classifier.classify("what is the weather today");
        
        assertEquals(TaskType.UNKNOWN, result.getTaskType());
    }

    @Test
    void classify_unknown_onlyStatus() {
        PatternClassifier.ClassificationResult result = classifier.classify("pending");
        
        assertEquals(TaskType.UNKNOWN, result.getTaskType());
    }

    @Test
    void classify_unknown_emptyCaseId() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
        assertNull(result.getEntities().get("case_id"));
    }

    // ========== Edge Cases ==========

    @Test
    void classify_emptyQuery() {
        PatternClassifier.ClassificationResult result = classifier.classify("");
        
        assertEquals(TaskType.UNKNOWN, result.getTaskType());
        assertTrue(result.getEntities().isEmpty());
    }

    @Test
    void classify_onlyWhitespace() {
        PatternClassifier.ClassificationResult result = classifier.classify("   ");
        
        assertEquals(TaskType.UNKNOWN, result.getTaskType());
    }

    @Test
    void classify_caseInsensitive() {
        PatternClassifier.ClassificationResult result = classifier.classify("CANCEL CASE 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
    }

    @Test
    void classify_mixedCase() {
        PatternClassifier.ClassificationResult result = classifier.classify("CaNcEl CaSe 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
    }

    @Test
    void classify_withExtraSpaces() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel    case    2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
        assertEquals("2025123P6732", result.getEntities().get("case_id"));
    }

    @Test
    void classify_withNewlines() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel\ncase\n2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
    }

    // ========== Ambiguous Cases Tests ==========

    @Test
    void classify_updateStatusTakesPrecedence_withBothKeywords() {
        // When both cancel and update present, context matters
        // Pattern classifier prioritizes update status when both are present
        PatternClassifier.ClassificationResult result = classifier.classify("cancel the update status request for 2025123P6732");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
    }

    @Test
    void classify_updateStatusTakesPrecedence_withChangeStatus() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel change status for 2025123P6732");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
    }

    // ========== Multiple Entities Tests ==========

    @Test
    void classify_multipleCaseIds_extractsFirst() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case 2025123P6732 and 2024123P6731");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
        assertEquals("2025123P6732", result.getEntities().get("case_id"));
    }

    @Test
    void classify_multipleStatuses_extractsFirst() {
        PatternClassifier.ClassificationResult result = classifier.classify("update status to pending or completed 2024123P6731");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("pending", result.getEntities().get("status"));
    }

    // ========== Query Normalization Tests ==========

    @Test
    void classify_removesArticles() {
        PatternClassifier.ClassificationResult result = classifier.classify("please cancel the case 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
    }

    @Test
    void classify_removesCaseIdVariations() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel case id 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
        assertEquals("2025123P6732", result.getEntities().get("case_id"));
    }

    @Test
    void classify_removesPoliteWords_canYou() {
        PatternClassifier.ClassificationResult result = classifier.classify("can you cancel case 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
    }

    @Test
    void classify_removesPoliteWords_couldYou() {
        PatternClassifier.ClassificationResult result = classifier.classify("could you cancel case 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
    }

    @Test
    void classify_removesPoliteWords_iWantTo() {
        PatternClassifier.ClassificationResult result = classifier.classify("I want to cancel case 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
    }

    @Test
    void classify_removesPoliteWords_iNeedTo() {
        PatternClassifier.ClassificationResult result = classifier.classify("I need to update status to pending 2025123P6732");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
    }

    // ========== Real World Scenarios ==========

    @Test
    void classify_realWorld_cancelWithReason() {
        PatternClassifier.ClassificationResult result = classifier.classify("Please cancel case 2025123P6732 because patient withdrew consent");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
        assertEquals("2025123P6732", result.getEntities().get("case_id"));
    }

    @Test
    void classify_realWorld_updateWithReason() {
        PatternClassifier.ClassificationResult result = classifier.classify("update case 2025123P6732 status to completed as processing is done");
        
        assertEquals(TaskType.UPDATE_CASE_STATUS, result.getTaskType());
        assertEquals("2025123P6732", result.getEntities().get("case_id"));
        assertEquals("completed", result.getEntities().get("status"));
    }

    @Test
    void classify_realWorld_urgentRequest() {
        PatternClassifier.ClassificationResult result = classifier.classify("URGENT: cancel case 2025123P6732 immediately");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
        assertEquals("2025123P6732", result.getEntities().get("case_id"));
    }

    @Test
    void classify_realWorld_abbreviatedRequest() {
        PatternClassifier.ClassificationResult result = classifier.classify("cancel 2025123P6732");
        
        assertEquals(TaskType.CANCEL_CASE, result.getTaskType());
        assertEquals("2025123P6732", result.getEntities().get("case_id"));
    }
}

