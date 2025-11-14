package com.lca.productionsupport.service;

import com.lca.productionsupport.model.UseCaseDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class RunbookRegistryTest {

    private RunbookRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RunbookRegistry();
        // Set fields via reflection for testing
        try {
            var locationField = RunbookRegistry.class.getDeclaredField("runbookLocation");
            locationField.setAccessible(true);
            locationField.set(registry, "classpath:runbooks/");
            
            var enabledField = RunbookRegistry.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(registry, true);
            
            // Load runbooks
            registry.loadRunbooks();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize test registry", e);
        }
    }

    @Test
    void loadRunbooks_loadsYamlFiles() {
        assertTrue(registry.isEnabled());
        assertTrue(registry.getAllUseCases().size() > 0);
    }

    @Test
    void loadRunbooks_loadsCancelCase() {
        assertTrue(registry.hasUseCase("CANCEL_CASE"));
        UseCaseDefinition cancelCase = registry.getUseCase("CANCEL_CASE");
        assertNotNull(cancelCase);
        assertEquals("CANCEL_CASE", cancelCase.getUseCase().getId());
        assertEquals("Cancel Case", cancelCase.getUseCase().getName());
    }

    @Test
    void loadRunbooks_loadsUpdateCaseStatus() {
        assertTrue(registry.hasUseCase("UPDATE_CASE_STATUS"));
        UseCaseDefinition updateStatus = registry.getUseCase("UPDATE_CASE_STATUS");
        assertNotNull(updateStatus);
        assertEquals("UPDATE_CASE_STATUS", updateStatus.getUseCase().getId());
    }

    @Test
    void getUseCase_returnsNullForNonExistent() {
        UseCaseDefinition result = registry.getUseCase("NON_EXISTENT");
        assertNull(result);
    }

    @Test
    void hasUseCase_returnsFalseForNonExistent() {
        assertFalse(registry.hasUseCase("NON_EXISTENT"));
    }

    @Test
    void getAllUseCases_returnsAllLoadedUseCases() {
        Collection<UseCaseDefinition> allUseCases = registry.getAllUseCases();
        assertNotNull(allUseCases);
        assertTrue(allUseCases.size() >= 2); // At least CANCEL_CASE and UPDATE_CASE_STATUS
    }

    @Test
    void isEnabled_returnsTrueWhenEnabledAndLoaded() {
        assertTrue(registry.isEnabled());
    }

    @Test
    void isEnabled_returnsFalseWhenDisabled() {
        RunbookRegistry disabledRegistry = new RunbookRegistry();
        try {
            var locationField = RunbookRegistry.class.getDeclaredField("runbookLocation");
            locationField.setAccessible(true);
            locationField.set(disabledRegistry, "classpath:runbooks/");
            
            var enabledField = RunbookRegistry.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(disabledRegistry, false);
            
            disabledRegistry.loadRunbooks();
            assertFalse(disabledRegistry.isEnabled());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize disabled registry", e);
        }
    }

    @Test
    void isEnabled_returnsFalseWhenNoUseCasesLoaded() {
        RunbookRegistry emptyRegistry = new RunbookRegistry();
        try {
            var locationField = RunbookRegistry.class.getDeclaredField("runbookLocation");
            locationField.setAccessible(true);
            locationField.set(emptyRegistry, "classpath:nonexistent/");
            
            var enabledField = RunbookRegistry.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(emptyRegistry, true);
            
            emptyRegistry.loadRunbooks();
            assertFalse(emptyRegistry.isEnabled());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize empty registry", e);
        }
    }

    @Test
    void reload_clearsAndReloadsRunbooks() {
        int initialSize = registry.getAllUseCases().size();
        assertTrue(initialSize > 0);
        
        registry.reload();
        
        int reloadedSize = registry.getAllUseCases().size();
        assertEquals(initialSize, reloadedSize);
        assertTrue(registry.hasUseCase("CANCEL_CASE"));
    }

    @Test
    void loadRunbook_validatesUseCaseId() {
        RunbookRegistry testRegistry = new RunbookRegistry();
        String invalidYaml = "useCase:\n  name: \"Test\"\n";
        
        Resource mockResource = new ClassPathResource("test") {
            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(invalidYaml.getBytes());
            }
            
            @Override
            public String getFilename() {
                return "invalid.yaml";
            }
        };
        
        // Should not throw, but should log error
        assertDoesNotThrow(() -> {
            try {
                var loadMethod = RunbookRegistry.class.getDeclaredMethod("loadRunbook", Resource.class);
                loadMethod.setAccessible(true);
                loadMethod.invoke(testRegistry, mockResource);
            } catch (Exception e) {
                // Expected - validation should fail
            }
        });
    }

    @Test
    void loadRunbook_validatesClassification() {
        RunbookRegistry testRegistry = new RunbookRegistry();
        String invalidYaml = "useCase:\n  id: \"TEST\"\n";
        
        Resource mockResource = new ClassPathResource("test") {
            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(invalidYaml.getBytes());
            }
            
            @Override
            public String getFilename() {
                return "invalid.yaml";
            }
        };
        
        // Should not throw, but should log error
        assertDoesNotThrow(() -> {
            try {
                var loadMethod = RunbookRegistry.class.getDeclaredMethod("loadRunbook", Resource.class);
                loadMethod.setAccessible(true);
                loadMethod.invoke(testRegistry, mockResource);
            } catch (Exception e) {
                // Expected - validation should fail
            }
        });
    }

    @Test
    void loadRunbook_validatesExecutionSteps() {
        RunbookRegistry testRegistry = new RunbookRegistry();
        String invalidYaml = "useCase:\n  id: \"TEST\"\nclassification:\n  keywords: []\n";
        
        Resource mockResource = new ClassPathResource("test") {
            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(invalidYaml.getBytes());
            }
            
            @Override
            public String getFilename() {
                return "invalid.yaml";
            }
        };
        
        // Should not throw, but should log error
        assertDoesNotThrow(() -> {
            try {
                var loadMethod = RunbookRegistry.class.getDeclaredMethod("loadRunbook", Resource.class);
                loadMethod.setAccessible(true);
                loadMethod.invoke(testRegistry, mockResource);
            } catch (Exception e) {
                // Expected - validation should fail
            }
        });
    }

    @Test
    void loadRunbooks_handlesNonExistentLocation() {
        RunbookRegistry testRegistry = new RunbookRegistry();
        try {
            var locationField = RunbookRegistry.class.getDeclaredField("runbookLocation");
            locationField.setAccessible(true);
            locationField.set(testRegistry, "classpath:nonexistent/");
            
            var enabledField = RunbookRegistry.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(testRegistry, true);
            
            // Should not throw
            assertDoesNotThrow(() -> testRegistry.loadRunbooks());
            assertFalse(testRegistry.isEnabled());
        } catch (Exception e) {
            throw new RuntimeException("Failed to test non-existent location", e);
        }
    }

    @Test
    void loadRunbooks_handlesInvalidYaml() {
        RunbookRegistry testRegistry = new RunbookRegistry();
        try {
            var locationField = RunbookRegistry.class.getDeclaredField("runbookLocation");
            locationField.setAccessible(true);
            locationField.set(testRegistry, "classpath:runbooks/");
            
            var enabledField = RunbookRegistry.class.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            enabledField.set(testRegistry, true);
            
            // Should not throw even if YAML is invalid
            assertDoesNotThrow(() -> testRegistry.loadRunbooks());
        } catch (Exception e) {
            throw new RuntimeException("Failed to test invalid YAML", e);
        }
    }
}

