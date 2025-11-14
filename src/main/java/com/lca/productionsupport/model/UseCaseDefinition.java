package com.lca.productionsupport.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Root YAML structure for a runbook definition
 */
@Data
public class UseCaseDefinition {
    private UseCaseInfo useCase;
    private ClassificationConfig classification;
    private ExtractionConfig extraction;
    private ExecutionConfig execution;
    private RollbackConfig rollback;
    private List<String> warnings;
    private MetadataConfig metadata;

    @Data
    public static class UseCaseInfo {
        private String id;
        private String name;
        private String description;
        private String category;
        private String version;
        private String downstreamService;
    }

    @Data
    public static class ClassificationConfig {
        private List<String> keywords;
        private Map<String, List<String>> synonyms;
        private Double minConfidence;
        private List<String> requiredEntities;
    }

    @Data
    public static class ExtractionConfig {
        private Map<String, EntityConfig> entities;
    }

    @Data
    public static class EntityConfig {
        private String type;
        private List<String> patterns;
        private boolean required;
        private ValidationConfig validation;
        private String transform;
    }

    @Data
    public static class ValidationConfig {
        private String regex;
        private List<String> enumValues;
        private String errorMessage;
    }

    @Data
    public static class ExecutionConfig {
        private Integer timeout;
        private RetryPolicy retryPolicy;
        private List<StepDefinition> steps;
    }

    @Data
    public static class RetryPolicy {
        private int maxAttempts;
        private long backoffMs;
    }

    @Data
    public static class StepDefinition {
        private int stepNumber;
        private String name;
        private String description;
        private String stepType; // prechecks, procedure, postchecks, rollback
        private boolean autoExecutable;
        private String method; // LOCAL_MESSAGE, HEADER_CHECK, GET, POST, PATCH, DELETE
        private String path;
        private Map<String, Object> body;
        private Map<String, String> headers;
        private int expectedStatus;
        private ValidationConfig validation;
        private ErrorHandling errorHandling;
        private boolean optional;
        private String expectedResponse;
        private String localMessage;
    }

    @Data
    public static class ErrorHandling {
        private String onFailure; // abort, rollback, alert, continue
        private String message;
    }

    @Data
    public static class RollbackConfig {
        private boolean enabled;
        private List<StepDefinition> steps;
    }

    @Data
    public static class MetadataConfig {
        private String author;
        private String lastModified;
        private List<String> tags;
    }
}

