package com.lca.productionsupport.service;

import com.lca.productionsupport.model.TaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple pattern matching classifier using keywords and regex.
 * No AI, embeddings, or vector search - just straightforward pattern matching.
 */
@Slf4j
@Service
public class PatternClassifier {

    // Regex patterns for entity extraction
    // Matches formats like: CASE-2024-001, CASE2024001, 2025123P6732, 2024123P6731
    private static final Pattern CASE_ID_PATTERN = Pattern.compile(
        "(?i)\\b(?:case[-_\\s]?)?([0-9]{4,}[Pp]?[0-9]+)\\b"
    );
    
    // Status pattern - more flexible, handles common typos
    private static final Pattern STATUS_PATTERN = Pattern.compile(
        "(?i)\\b(pending|accessioning?|grossing|embedding|cutting|staining|microscopy|microtomy|pathologist[_\\s]?review|rostering|under[_\\s]?review|on[_\\s]?hold|completed?|cancell?ed|archived?|closed?)\\b"
    );
    
    /**
     * Classify the user query and extract entities
     */
    public ClassificationResult classify(String query) {
        log.info("Classifying query: {}", query);
        
        // Normalize: remove polite words, extra spaces, and convert to lowercase
        String normalizedQuery = normalizeQuery(query);
        Map<String, String> entities = new HashMap<>();
        
        // Extract case ID
        Matcher caseIdMatcher = CASE_ID_PATTERN.matcher(query);
        if (caseIdMatcher.find()) {
            String caseId = caseIdMatcher.group(1);
            entities.put("case_id", caseId);
            log.debug("Extracted case_id: {}", caseId);
        }
        
        // Extract status
        Matcher statusMatcher = STATUS_PATTERN.matcher(query);
        if (statusMatcher.find()) {
            String status = normalizeStatus(statusMatcher.group(1));
            entities.put("status", status);
            log.debug("Extracted status: {}", status);
        }
        
        // Pattern matching for task classification
        TaskType taskType = classifyTask(normalizedQuery, entities);
        
        log.info("Classified as {} with entities: {}", taskType, entities);
        
        return ClassificationResult.builder()
            .taskType(taskType)
            .entities(entities)
            .build();
    }
    
    /**
     * Normalize query by removing polite words and standardizing format
     */
    private String normalizeQuery(String query) {
        String normalized = query.toLowerCase();
        
        // Remove common polite/filler words
        normalized = normalized.replaceAll("\\b(please|kindly|can you|could you|would you|i want to|i need to)\\b", "");
        
        // Remove extra articles
        normalized = normalized.replaceAll("\\b(a|an|the)\\b", " ");
        
        // Normalize spacing around "case id" variations
        normalized = normalized.replaceAll("\\bcase\\s*id\\b", "case");
        
        // Clean up multiple spaces
        normalized = normalized.replaceAll("\\s+", " ").trim();
        
        return normalized;
    }
    
    /**
     * Normalize status to standard format
     */
    private String normalizeStatus(String status) {
        String normalized = status.toLowerCase()
            .replaceAll("_", " ")
            .replaceAll("\\s+", "_");
        
        // Fix common typos and variations
        if (normalized.startsWith("accession")) {
            return "accessioning";
        }
        if (normalized.contains("pathologist") && normalized.contains("review")) {
            return "pathologist_review";
        }
        if (normalized.contains("review") && !normalized.contains("pathologist")) {
            return "under_review";
        }
        if (normalized.contains("hold")) {
            return "on_hold";
        }
        
        return normalized;
    }
    
    /**
     * Classify the task based on normalized query and entities
     */
    private TaskType classifyTask(String normalizedQuery, Map<String, String> entities) {
        // CANCEL_CASE patterns - check for cancel/delete/remove keywords
        if (containsAny(normalizedQuery, "cancel", "cancellation", "abort", "delete", "remove", 
                       "stop case", "terminate", "drop")) {
            // Make sure it's not about cancelling a status update
            if (!containsAny(normalizedQuery, "update", "change status", "set status")) {
                return TaskType.CANCEL_CASE;
            }
        }
        
        // UPDATE_CASE_STATUS patterns - explicit status update phrases
        if (containsAny(normalizedQuery, "update status", "change status", "set status", 
                       "mark status", "status to", "mark as", "move to", "transition to",
                       "mark to")) {
            return TaskType.UPDATE_CASE_STATUS;
        }
        
        // If query has a status keyword and action verbs, it's likely a status update
        if (containsStatus(normalizedQuery) && 
            containsAny(normalizedQuery, "set", "mark", "change", "move", "transition", "update", "to")) {
            return TaskType.UPDATE_CASE_STATUS;
        }
        
        // Fallback: if we have both case_id and status, assume UPDATE_CASE_STATUS
        if (entities.containsKey("case_id") && entities.containsKey("status")) {
            return TaskType.UPDATE_CASE_STATUS;
        }
        
        return TaskType.UNKNOWN;
    }
    
    /**
     * Check if query contains any of the given keywords
     */
    private boolean containsAny(String query, String... keywords) {
        for (String keyword : keywords) {
            if (query.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if query contains a status keyword
     */
    private boolean containsStatus(String query) {
        String[] statuses = {"pending", "accession", "grossing", "embedding", "cutting", 
                           "staining", "microscopy", "microtomy", "pathologist", "rostering",
                           "review", "hold", "completed", "cancelled", "cancel", "archived", "closed"};
        return containsAny(query, statuses);
    }
    
    /**
     * Classification result
     */
    public static class ClassificationResult {
        private final TaskType taskType;
        private final Map<String, String> entities;
        
        private ClassificationResult(TaskType taskType, Map<String, String> entities) {
            this.taskType = taskType;
            this.entities = entities;
        }
        
        public static ClassificationResultBuilder builder() {
            return new ClassificationResultBuilder();
        }
        
        public TaskType getTaskType() { return taskType; }
        public Map<String, String> getEntities() { return entities; }
        
        public static class ClassificationResultBuilder {
            private TaskType taskType;
            private Map<String, String> entities;
            
            public ClassificationResultBuilder taskType(TaskType taskType) {
                this.taskType = taskType;
                return this;
            }
            
            public ClassificationResultBuilder entities(Map<String, String> entities) {
                this.entities = entities;
                return this;
            }
            
            public ClassificationResult build() {
                return new ClassificationResult(taskType, entities);
            }
        }
    }
}

