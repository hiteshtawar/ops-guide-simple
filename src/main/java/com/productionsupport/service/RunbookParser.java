package com.productionsupport.service;

import com.productionsupport.model.OperationalResponse.RunbookStep;
import com.productionsupport.model.TaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses runbook markdown files to extract executable steps
 */
@Slf4j
@Service
public class RunbookParser {

    private final Map<String, List<RunbookStep>> cachedRunbooks = new HashMap<>();
    
    // Patterns for parsing markdown
    private static final Pattern API_CALL_PATTERN = Pattern.compile("(GET|POST|PATCH|PUT|DELETE)\\s+(/[^\\s]+)");
    private static final Pattern SECTION_PATTERN = Pattern.compile("^##\\s+(.+)$");
    
    public RunbookParser() {
        // Pre-load runbooks on startup
        loadRunbook(TaskType.CANCEL_CASE.name(), "runbooks/cancel-case-runbook.md");
        loadRunbook(TaskType.UPDATE_CASE_STATUS.name(), "runbooks/update-case-status-runbook.md");
    }
    
    /**
     * Get steps for a specific task ID
     */
    public List<RunbookStep> getSteps(String taskId, String stepType) {
        List<RunbookStep> allSteps = cachedRunbooks.get(taskId);
        if (allSteps == null) {
            log.warn("No runbook found for task ID: {}", taskId);
            return new ArrayList<>();
        }
        
        // Filter by step type if specified
        if (stepType != null && !stepType.isEmpty()) {
            return allSteps.stream()
                .filter(step -> stepType.equalsIgnoreCase(step.getStepType()))
                .toList();
        }
        
        return allSteps;
    }
    
    /**
     * Get a specific step by number
     */
    public RunbookStep getStep(String taskId, int stepNumber) {
        List<RunbookStep> steps = cachedRunbooks.get(taskId);
        if (steps == null || stepNumber < 1 || stepNumber > steps.size()) {
            return null;
        }
        return steps.get(stepNumber - 1);
    }
    
    /**
     * Load and parse a runbook markdown file
     */
    private void loadRunbook(String taskId, String filePath) {
        try {
            log.info("Loading runbook: {} from {}", taskId, filePath);
            ClassPathResource resource = new ClassPathResource(filePath);
            List<RunbookStep> steps = parseMarkdown(resource);
            cachedRunbooks.put(taskId, steps);
            log.info("Loaded {} steps for {}", steps.size(), taskId);
        } catch (IOException e) {
            log.error("Failed to load runbook: {}", filePath, e);
        }
    }
    
    /**
     * Parse markdown content into structured steps
     */
    private List<RunbookStep> parseMarkdown(ClassPathResource resource) throws IOException {
        List<RunbookStep> steps = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            String currentSection = null;
            StringBuilder stepDescription = new StringBuilder();
            StringBuilder codeBlock = new StringBuilder();
            boolean inCodeBlock = false;
            int stepNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                // Track sections (Pre-checks, Procedure, Post-checks, Rollback)
                Matcher sectionMatcher = SECTION_PATTERN.matcher(line);
                if (sectionMatcher.matches()) {
                    String sectionName = sectionMatcher.group(1).toLowerCase();
                    if (sectionName.contains("pre-check")) {
                        currentSection = "precheck";
                    } else if (sectionName.contains("procedure")) {
                        currentSection = "procedure";
                    } else if (sectionName.contains("post-check")) {
                        currentSection = "postcheck";
                    } else if (sectionName.contains("rollback")) {
                        currentSection = "rollback";
                    }
                    continue;
                }
                
                // Track code blocks
                if (line.trim().startsWith("```")) {
                    if (inCodeBlock) {
                        // End of code block - create a step
                        if (currentSection != null) {
                            String code = codeBlock.toString().trim();
                            RunbookStep step = parseCodeBlockToStep(code, stepDescription.toString().trim(), 
                                                                    currentSection, ++stepNumber);
                            if (step != null) {
                                steps.add(step);
                            }
                        }
                        codeBlock = new StringBuilder();
                        stepDescription = new StringBuilder();
                        inCodeBlock = false;
                    } else {
                        inCodeBlock = true;
                    }
                    continue;
                }
                
                if (inCodeBlock) {
                    codeBlock.append(line).append("\n");
                } else if (currentSection != null && !line.trim().isEmpty() && !line.startsWith("#")) {
                    // Capture step description (text before code block)
                    // Pattern: "1. **Step Name**" -> extract just "Step Name"
                    if (line.matches("^\\d+\\.\\s+\\*\\*(.+)\\*\\*.*$")) {
                        String match = line.replaceAll("^\\d+\\.\\s+\\*\\*(.+)\\*\\*.*$", "$1").trim();
                        stepDescription.append(match);
                    }
                }
            }
        }
        
        return steps;
    }
    
    /**
     * Parse a code block into a structured step
     */
    private RunbookStep parseCodeBlockToStep(String code, String description, String stepType, int stepNumber) {
        // Extract HTTP method and path
        Matcher apiMatcher = API_CALL_PATTERN.matcher(code);
        
        if (!apiMatcher.find()) {
            // Not an API call, might be a shell command or note
            return null;
        }
        
        String method = apiMatcher.group(1);
        String path = apiMatcher.group(2);
        
        // Extract request body (look for JSON between { and })
        String requestBody = null;
        if (code.contains("{")) {
            int start = code.indexOf("{");
            int end = code.lastIndexOf("}");
            if (start > 0 && end > start) {
                requestBody = code.substring(start, end + 1).trim();
            }
        }
        
        // Determine if auto-executable (GET requests are generally safe)
        boolean autoExecutable = "GET".equals(method);
        
        return RunbookStep.builder()
            .stepNumber(stepNumber)
            .description(description.isEmpty() ? extractDescription(code) : description)
            .method(method)
            .path(path)
            .requestBody(requestBody)
            .autoExecutable(autoExecutable)
            .stepType(stepType)
            .build();
    }
    
    /**
     * Extract description from code comments
     */
    private String extractDescription(String code) {
        // Look for comment lines starting with #
        String[] lines = code.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("#") && !line.startsWith("##")) {
                return line.substring(1).trim();
            }
        }
        return "API call";
    }
}

