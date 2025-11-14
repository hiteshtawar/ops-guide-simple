package com.lca.productionsupport.service;

import com.lca.productionsupport.model.UseCaseDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Classifies natural language queries using YAML runbook definitions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunbookClassifier {
    
    private final RunbookRegistry registry;
    
    /**
     * Classify a natural language query and return the best matching use case ID
     */
    public String classify(String query) {
        if (!registry.isEnabled()) {
            log.debug("Runbook classification disabled, returning UNKNOWN");
            return "UNKNOWN";
        }
        
        log.debug("Classifying query: {}", query);
        
        String normalizedQuery = query.toLowerCase().trim();
        
        // Score each use case
        Map<String, Double> scores = new HashMap<>();
        
        for (UseCaseDefinition useCase : registry.getAllUseCases()) {
            double score = calculateScore(normalizedQuery, useCase);
            if (score > 0) {
                scores.put(useCase.getUseCase().getId(), score);
                log.debug("Use case {} scored: {}", useCase.getUseCase().getId(), score);
            }
        }
        
        if (scores.isEmpty()) {
            log.warn("No matching use case found for query: {}", query);
            return "UNKNOWN";
        }
        
        // Get highest scoring use case
        String bestMatch = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("UNKNOWN");
        
        log.info("Classified as: {} (score: {})", bestMatch, scores.get(bestMatch));
        
        return bestMatch;
    }
    
    private double calculateScore(String query, UseCaseDefinition useCase) {
        double score = 0.0;
        var classification = useCase.getClassification();
        
        // Check keywords
        if (classification.getKeywords() != null) {
            for (String keyword : classification.getKeywords()) {
                if (query.contains(keyword.toLowerCase())) {
                    score += 1.0;
                }
            }
        }
        
        // Check synonyms
        if (classification.getSynonyms() != null) {
            for (Map.Entry<String, List<String>> entry : classification.getSynonyms().entrySet()) {
                for (String synonym : entry.getValue()) {
                    if (query.contains(synonym.toLowerCase())) {
                        score += 0.5;
                    }
                }
            }
        }
        
        // Apply minimum confidence threshold
        Double minConfidence = classification.getMinConfidence();
        if (minConfidence != null && score < minConfidence) {
            return 0.0;
        }
        
        return score;
    }
    
    /**
     * Return all use cases that match the query (for ambiguous cases)
     */
    public List<String> classifyMultiple(String query) {
        if (!registry.isEnabled()) {
            return Collections.emptyList();
        }
        
        String normalizedQuery = query.toLowerCase().trim();
        
        return registry.getAllUseCases().stream()
            .filter(useCase -> calculateScore(normalizedQuery, useCase) > 0)
            .map(useCase -> useCase.getUseCase().getId())
            .collect(Collectors.toList());
    }
}

