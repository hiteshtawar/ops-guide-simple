package com.lca.productionsupport.service;

import com.lca.productionsupport.model.UseCaseDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.representer.Representer;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that loads and manages all YAML-based runbook definitions
 */
@Slf4j
@Service
public class RunbookRegistry {
    
    @Value("${runbook.location:classpath:runbooks/}")
    private String runbookLocation;
    
    @Value("${runbook.enabled:true}")
    private boolean enabled;
    
    private final Map<String, UseCaseDefinition> useCases = new ConcurrentHashMap<>();
    private final Yaml yaml;
    
    public RunbookRegistry() {
        // Initialize YAML parser with proper settings for SnakeYAML 2.0
        LoaderOptions loaderOptions = new LoaderOptions();
        DumperOptions dumperOptions = new DumperOptions();
        Representer representer = new Representer(dumperOptions);
        representer.getPropertyUtils().setSkipMissingProperties(true);
        this.yaml = new Yaml(new Constructor(UseCaseDefinition.class, loaderOptions), representer);
    }
    
    @PostConstruct
    public void loadRunbooks() {
        if (!enabled) {
            log.info("Dynamic runbooks are disabled");
            return;
        }
        
        log.info("Loading runbooks from: {}", runbookLocation);
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(runbookLocation + "*.yaml");
            
            if (resources.length == 0) {
                log.warn("No YAML runbooks found at: {}", runbookLocation);
                return;
            }
            
            for (Resource resource : resources) {
                loadRunbook(resource);
            }
            
            log.info("Successfully loaded {} runbooks: {}", useCases.size(), useCases.keySet());
        } catch (Exception e) {
            log.error("Failed to load runbooks from: {}", runbookLocation, e);
            // Don't throw - allow service to start even if runbooks fail to load
        }
    }
    
    private void loadRunbook(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            UseCaseDefinition definition = yaml.load(is);
            
            // Validate
            validateRunbook(definition);
            
            // Register
            useCases.put(definition.getUseCase().getId(), definition);
            
            log.debug("Loaded runbook: {} from {}", 
                     definition.getUseCase().getId(), 
                     resource.getFilename());
        } catch (Exception e) {
            log.error("Failed to load runbook: {}", resource.getFilename(), e);
        }
    }
    
    private void validateRunbook(UseCaseDefinition definition) {
        if (definition.getUseCase() == null || definition.getUseCase().getId() == null) {
            throw new IllegalArgumentException("Runbook must have useCase.id");
        }
        if (definition.getClassification() == null) {
            throw new IllegalArgumentException("Runbook must have classification section");
        }
        if (definition.getExecution() == null || definition.getExecution().getSteps() == null) {
            throw new IllegalArgumentException("Runbook must have execution.steps");
        }
    }
    
    public UseCaseDefinition getUseCase(String id) {
        return useCases.get(id);
    }
    
    public Collection<UseCaseDefinition> getAllUseCases() {
        return useCases.values();
    }
    
    public boolean hasUseCase(String id) {
        return useCases.containsKey(id);
    }
    
    public boolean isEnabled() {
        return enabled && !useCases.isEmpty();
    }
    
    // For hot-reload (optional)
    public void reload() {
        useCases.clear();
        loadRunbooks();
    }
}

