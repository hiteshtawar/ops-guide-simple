package com.lca.productionsupport.service;

import com.lca.productionsupport.model.UseCaseDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RunbookEntityExtractorTest {

    private RunbookEntityExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new RunbookEntityExtractor();
    }

    @Test
    void extract_caseId_matchesPattern() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("(\\d{7}[A-Z]\\d{4})"));
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        Map<String, String> result = extractor.extract("cancel case 2025123P6732", config);
        
        assertEquals("2025123P6732", result.get("case_id"));
    }

    @Test
    void extract_caseId_withMultiplePatterns() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of(
            "case\\s+(\\d{7}[A-Z]\\d{4})",
            "(\\d{7}[A-Z]\\d{4})"
        ));
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        Map<String, String> result = extractor.extract("case 2025123P6732", config);
        assertEquals("2025123P6732", result.get("case_id"));
    }

    @Test
    void extract_status_matchesPattern() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("status\\s+to\\s+(\\w+)"));
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("status", entityConfig));
        
        Map<String, String> result = extractor.extract("update status to pending", config);
        assertEquals("pending", result.get("status"));
    }

    @Test
    void extract_withTransform_lowercase() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("status\\s+to\\s+(\\w+)"));
        entityConfig.setTransform("lowercase");
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("status", entityConfig));
        
        Map<String, String> result = extractor.extract("update status to PENDING", config);
        assertEquals("pending", result.get("status"));
    }

    @Test
    void extract_withTransform_uppercase() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("status\\s+to\\s+(\\w+)"));
        entityConfig.setTransform("uppercase");
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("status", entityConfig));
        
        Map<String, String> result = extractor.extract("update status to pending", config);
        assertEquals("PENDING", result.get("status"));
    }

    @Test
    void extract_withTransform_trim() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("value\\s+is\\s+(\\s+\\w+\\s+)"));
        entityConfig.setTransform("trim");
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("value", entityConfig));
        
        Map<String, String> result = extractor.extract("value is  test  ", config);
        assertEquals("test", result.get("value"));
    }

    @Test
    void extract_withValidation_regex_passes() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("(\\d{7}[A-Z]\\d{4})"));
        UseCaseDefinition.ValidationConfig validation = new UseCaseDefinition.ValidationConfig();
        validation.setRegex("^\\d{7}[A-Z]\\d{4}$");
        entityConfig.setValidation(validation);
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        Map<String, String> result = extractor.extract("case 2025123P6732", config);
        assertEquals("2025123P6732", result.get("case_id"));
    }

    @Test
    void extract_withValidation_regex_fails() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("(\\d+)"));
        UseCaseDefinition.ValidationConfig validation = new UseCaseDefinition.ValidationConfig();
        validation.setRegex("^\\d{7}[A-Z]\\d{4}$");
        entityConfig.setValidation(validation);
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        Map<String, String> result = extractor.extract("case 123", config);
        assertNull(result.get("case_id")); // Should not extract due to validation failure
    }

    @Test
    void extract_withValidation_enum_passes() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("status\\s+to\\s+(\\w+)"));
        UseCaseDefinition.ValidationConfig validation = new UseCaseDefinition.ValidationConfig();
        validation.setEnumValues(List.of("pending", "completed", "cancelled"));
        entityConfig.setValidation(validation);
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("status", entityConfig));
        
        Map<String, String> result = extractor.extract("update status to pending", config);
        assertEquals("pending", result.get("status"));
    }

    @Test
    void extract_withValidation_enum_fails() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("status\\s+to\\s+(\\w+)"));
        UseCaseDefinition.ValidationConfig validation = new UseCaseDefinition.ValidationConfig();
        validation.setEnumValues(List.of("pending", "completed", "cancelled"));
        entityConfig.setValidation(validation);
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("status", entityConfig));
        
        Map<String, String> result = extractor.extract("update status to invalid", config);
        assertNull(result.get("status")); // Should not extract due to validation failure
    }

    @Test
    void extract_withValidation_enum_caseInsensitive() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("status\\s+to\\s+(\\w+)"));
        UseCaseDefinition.ValidationConfig validation = new UseCaseDefinition.ValidationConfig();
        validation.setEnumValues(List.of("pending", "completed", "cancelled"));
        entityConfig.setValidation(validation);
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("status", entityConfig));
        
        Map<String, String> result = extractor.extract("update status to PENDING", config);
        assertEquals("PENDING", result.get("status")); // Value is extracted, enum check is case-insensitive
    }

    @Test
    void extract_requiredEntity_missing_returnsEmpty() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("(\\d{7}[A-Z]\\d{4})"));
        entityConfig.setRequired(true);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        Map<String, String> result = extractor.extract("cancel case", config);
        assertNull(result.get("case_id"));
        // Should log warning but not throw
    }

    @Test
    void extract_optionalEntity_missing_returnsEmpty() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("(\\d{7}[A-Z]\\d{4})"));
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        Map<String, String> result = extractor.extract("cancel case", config);
        assertNull(result.get("case_id"));
    }

    @Test
    void extract_multipleEntities() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        
        UseCaseDefinition.EntityConfig caseIdConfig = new UseCaseDefinition.EntityConfig();
        caseIdConfig.setPatterns(List.of("(\\d{7}[A-Z]\\d{4})"));
        caseIdConfig.setRequired(false);
        
        UseCaseDefinition.EntityConfig statusConfig = new UseCaseDefinition.EntityConfig();
        statusConfig.setPatterns(List.of("status\\s+to\\s+(\\w+)"));
        statusConfig.setRequired(false);
        
        config.setEntities(Map.of(
            "case_id", caseIdConfig,
            "status", statusConfig
        ));
        
        Map<String, String> result = extractor.extract("update status to pending for case 2025123P6732", config);
        assertEquals("2025123P6732", result.get("case_id"));
        assertEquals("pending", result.get("status"));
    }

    @Test
    void extract_withNullConfig_returnsEmpty() {
        Map<String, String> result = extractor.extract("any query", null);
        assertTrue(result.isEmpty());
    }

    @Test
    void extract_withNullEntities_returnsEmpty() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        config.setEntities(null);
        
        Map<String, String> result = extractor.extract("any query", config);
        assertTrue(result.isEmpty());
    }

    @Test
    void extract_withNullPatterns_returnsEmpty() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(null);
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        Map<String, String> result = extractor.extract("any query", config);
        assertNull(result.get("case_id"));
    }

    @Test
    void extract_withEmptyPatterns_returnsEmpty() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of());
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        Map<String, String> result = extractor.extract("any query", config);
        assertNull(result.get("case_id"));
    }

    @Test
    void extract_withInvalidPattern_handlesGracefully() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("[invalid regex ("));
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        // Should not throw
        Map<String, String> result = extractor.extract("any query", config);
        assertNull(result.get("case_id"));
    }

    @Test
    void extract_withNullTransform_returnsOriginal() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("value\\s+is\\s+(\\w+)"));
        entityConfig.setTransform(null);
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("value", entityConfig));
        
        Map<String, String> result = extractor.extract("value is Test", config);
        assertEquals("Test", result.get("value"));
    }

    @Test
    void extract_withUnknownTransform_returnsOriginal() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("value\\s+is\\s+(\\w+)"));
        entityConfig.setTransform("unknown_transform");
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("value", entityConfig));
        
        Map<String, String> result = extractor.extract("value is Test", config);
        assertEquals("Test", result.get("value"));
    }

    @Test
    void extract_withNullValidation_returnsExtracted() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("(\\w+)"));
        entityConfig.setValidation(null);
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("value", entityConfig));
        
        Map<String, String> result = extractor.extract("test value", config);
        assertEquals("test", result.get("value"));
    }

    @Test
    void extract_withValidation_regexAndEnum_bothPass() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("status\\s+to\\s+(\\w+)"));
        UseCaseDefinition.ValidationConfig validation = new UseCaseDefinition.ValidationConfig();
        validation.setRegex("^\\w+$");
        validation.setEnumValues(List.of("pending", "completed"));
        entityConfig.setValidation(validation);
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("status", entityConfig));
        
        Map<String, String> result = extractor.extract("update status to pending", config);
        assertEquals("pending", result.get("status"));
    }

    @Test
    void extract_withValidation_regexPassesEnumFails() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("status\\s+to\\s+(\\w+)"));
        UseCaseDefinition.ValidationConfig validation = new UseCaseDefinition.ValidationConfig();
        validation.setRegex("^\\w+$");
        validation.setEnumValues(List.of("pending", "completed"));
        entityConfig.setValidation(validation);
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("status", entityConfig));
        
        Map<String, String> result = extractor.extract("update status to invalid", config);
        assertNull(result.get("status")); // Enum validation fails
    }

    @Test
    void extract_caseInsensitivePattern() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("case\\s+(\\d{7}[A-Z]\\d{4})"));
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        Map<String, String> result = extractor.extract("CASE 2025123P6732", config);
        assertEquals("2025123P6732", result.get("case_id"));
    }

    @Test
    void extract_withMultiplePatterns_firstMatchWins() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of(
            "pattern1\\s+(\\w+)",
            "pattern2\\s+(\\w+)"
        ));
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("value", entityConfig));
        
        Map<String, String> result = extractor.extract("pattern1 test", config);
        assertEquals("test", result.get("value"));
    }

    @Test
    void extract_withValidation_regexOnly_noEnum() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("(\\w+)"));
        UseCaseDefinition.ValidationConfig validation = new UseCaseDefinition.ValidationConfig();
        validation.setRegex("^[a-z]+$");
        validation.setEnumValues(null);
        entityConfig.setValidation(validation);
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("value", entityConfig));
        
        Map<String, String> result = extractor.extract("test", config);
        assertEquals("test", result.get("value"));
    }

    @Test
    void extract_withValidation_enumOnly_noRegex() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("(\\w+)"));
        UseCaseDefinition.ValidationConfig validation = new UseCaseDefinition.ValidationConfig();
        validation.setRegex(null);
        validation.setEnumValues(List.of("test", "value"));
        entityConfig.setValidation(validation);
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("value", entityConfig));
        
        Map<String, String> result = extractor.extract("test", config);
        assertEquals("test", result.get("value"));
    }

    @Test
    void extract_withTransform_trimAndLowercase() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("value\\s+is\\s+(\\w+)"));
        entityConfig.setTransform("lowercase");
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("value", entityConfig));
        
        Map<String, String> result = extractor.extract("value is TEST", config);
        assertEquals("test", result.get("value"));
    }

    @Test
    void extract_withRequiredEntity_missing_logsWarning() {
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("(\\d+)"));
        entityConfig.setRequired(true);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        Map<String, String> result = extractor.extract("no numbers here", config);
        assertNull(result.get("case_id"));
        // Should log warning but not throw
    }

    @Test
    void extract_withBrackets_trimsBrackets() {
        // Test that brackets are trimmed from extracted values
        // This handles cases where user copies example query with [placeholder]
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("case\\s+([\\w\\[\\]]+)"));
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        // Test with brackets around the value
        Map<String, String> result = extractor.extract("cancel case [2025123P6732]", config);
        assertEquals("2025123P6732", result.get("case_id"));
    }

    @Test
    void extract_withBracketsInPattern_trimsCorrectly() {
        // Test bracket trimming with a pattern that might capture brackets
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of("barcode\\s+is\\s+([A-Za-z0-9\\-_\\[\\]]+)"));
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("barcode", entityConfig));
        
        Map<String, String> result = extractor.extract("barcode is [BC123456]", config);
        assertEquals("BC123456", result.get("barcode"));
    }

    @Test
    void extract_caseId_withBracketsAndLongDigits_trimsBracketsAndPreservesAllDigits() {
        // Test that case ID with brackets and more than 4 digits after letter is extracted correctly
        // This tests the fix for issue where [2025251T115466] was being truncated to 2025251T1154
        UseCaseDefinition.ExtractionConfig config = new UseCaseDefinition.ExtractionConfig();
        UseCaseDefinition.EntityConfig entityConfig = new UseCaseDefinition.EntityConfig();
        entityConfig.setPatterns(List.of(
            "(?:a\\s+|the\\s+)?case\\s+([A-Za-z0-9\\-\\[\\]]+)",
            "(\\d{7,}[A-Z]\\d{4,})"
        ));
        entityConfig.setRequired(false);
        
        config.setEntities(Map.of("case_id", entityConfig));
        
        // Test with brackets
        Map<String, String> result1 = extractor.extract("cancel case [2025251T115466]", config);
        assertEquals("2025251T115466", result1.get("case_id"));
        
        // Test without brackets
        Map<String, String> result2 = extractor.extract("cancel case 2025251T115466", config);
        assertEquals("2025251T115466", result2.get("case_id"));
    }
}

