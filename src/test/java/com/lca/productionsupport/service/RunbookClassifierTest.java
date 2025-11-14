package com.lca.productionsupport.service;

import com.lca.productionsupport.model.UseCaseDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RunbookClassifierTest {

    private RunbookRegistry registry;
    private RunbookClassifier classifier;

    @BeforeEach
    void setUp() {
        registry = new TestRunbookRegistry();
        classifier = new RunbookClassifier(registry);
    }

    private static class TestRunbookRegistry extends RunbookRegistry {
        public TestRunbookRegistry() {
            super();
            try {
                var locationField = RunbookRegistry.class.getDeclaredField("runbookLocation");
                locationField.setAccessible(true);
                locationField.set(this, "classpath:runbooks/");
                
                var enabledField = RunbookRegistry.class.getDeclaredField("enabled");
                enabledField.setAccessible(true);
                enabledField.set(this, true);
                
                loadRunbooks();
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize test registry", e);
            }
        }
    }

    @Test
    void classify_cancelCase_matchesCorrectly() {
        String result = classifier.classify("cancel case 2025123P6732");
        assertEquals("CANCEL_CASE", result);
    }

    @Test
    void classify_cancelCase_withVariations() {
        assertEquals("CANCEL_CASE", classifier.classify("cancel a case"));
        assertEquals("CANCEL_CASE", classifier.classify("delete case"));
        assertEquals("CANCEL_CASE", classifier.classify("abort case"));
        assertEquals("CANCEL_CASE", classifier.classify("remove case"));
        assertEquals("CANCEL_CASE", classifier.classify("cancellation"));
    }

    @Test
    void classify_updateCaseStatus_matchesCorrectly() {
        String result = classifier.classify("update sample status to Completed - Microtomy");
        assertEquals("UPDATE_SAMPLE_STATUS", result);
    }

    @Test
    void classify_updateCaseStatus_withVariations() {
        assertEquals("UPDATE_SAMPLE_STATUS", classifier.classify("change sample status"));
        assertEquals("UPDATE_SAMPLE_STATUS", classifier.classify("update slide status"));
        assertEquals("UPDATE_SAMPLE_STATUS", classifier.classify("update container status"));
        assertEquals("UPDATE_SAMPLE_STATUS", classifier.classify("update block status"));
    }

    @Test
    void classify_unknownQuery_returnsUnknown() {
        String result = classifier.classify("hello world");
        assertEquals("UNKNOWN", result);
    }

    @Test
    void classify_emptyQuery_returnsUnknown() {
        String result = classifier.classify("");
        assertEquals("UNKNOWN", result);
    }

    @Test
    void classify_caseInsensitive() {
        assertEquals("CANCEL_CASE", classifier.classify("CANCEL CASE"));
        assertEquals("CANCEL_CASE", classifier.classify("Cancel Case"));
        assertEquals("CANCEL_CASE", classifier.classify("CaNcEl CaSe"));
    }

    @Test
    void classify_withWhitespace() {
        assertEquals("CANCEL_CASE", classifier.classify("  cancel case  "));
        assertEquals("CANCEL_CASE", classifier.classify("\t\ncancel case\n\t"));
    }

    @Test
    void classify_whenDisabled_returnsUnknown() {
        RunbookRegistry disabledRegistry = new RunbookRegistry() {
            @Override
            public boolean isEnabled() {
                return false;
            }
        };
        RunbookClassifier disabledClassifier = new RunbookClassifier(disabledRegistry);
        
        String result = disabledClassifier.classify("cancel case");
        assertEquals("UNKNOWN", result);
    }

    @Test
    void classifyMultiple_returnsAllMatches() {
        List<String> results = classifier.classifyMultiple("cancel case");
        assertTrue(results.contains("CANCEL_CASE"));
    }

    @Test
    void classifyMultiple_whenDisabled_returnsEmpty() {
        RunbookRegistry disabledRegistry = new RunbookRegistry() {
            @Override
            public boolean isEnabled() {
                return false;
            }
        };
        RunbookClassifier disabledClassifier = new RunbookClassifier(disabledRegistry);
        
        List<String> results = disabledClassifier.classifyMultiple("cancel case");
        assertTrue(results.isEmpty());
    }

    @Test
    void classifyMultiple_returnsMultipleMatches() {
        // Create a test registry with overlapping keywords
        RunbookRegistry testRegistry = new RunbookRegistry() {
            @Override
            public boolean isEnabled() {
                return true;
            }
            
            @Override
            public java.util.Collection<UseCaseDefinition> getAllUseCases() {
                UseCaseDefinition useCase1 = new UseCaseDefinition();
                UseCaseDefinition.UseCaseInfo info1 = new UseCaseDefinition.UseCaseInfo();
                info1.setId("CANCEL_CASE");
                useCase1.setUseCase(info1);
                
                UseCaseDefinition.ClassificationConfig classification1 = new UseCaseDefinition.ClassificationConfig();
                classification1.setKeywords(List.of("cancel", "case"));
                useCase1.setClassification(classification1);
                
                UseCaseDefinition useCase2 = new UseCaseDefinition();
                UseCaseDefinition.UseCaseInfo info2 = new UseCaseDefinition.UseCaseInfo();
                info2.setId("UPDATE_SAMPLE_STATUS");
                useCase2.setUseCase(info2);
                
                UseCaseDefinition.ClassificationConfig classification2 = new UseCaseDefinition.ClassificationConfig();
                classification2.setKeywords(List.of("update", "case"));
                useCase2.setClassification(classification2);
                
                return List.of(useCase1, useCase2);
            }
        };
        
        RunbookClassifier testClassifier = new RunbookClassifier(testRegistry);
        List<String> results = testClassifier.classifyMultiple("case");
        
        assertTrue(results.size() >= 1);
    }

    @Test
    void classify_withMinConfidence_belowThreshold_returnsUnknown() {
        RunbookRegistry testRegistry = new RunbookRegistry() {
            @Override
            public boolean isEnabled() {
                return true;
            }
            
            @Override
            public java.util.Collection<UseCaseDefinition> getAllUseCases() {
                UseCaseDefinition useCase = new UseCaseDefinition();
                UseCaseDefinition.UseCaseInfo info = new UseCaseDefinition.UseCaseInfo();
                info.setId("TEST_CASE");
                useCase.setUseCase(info);
                
                UseCaseDefinition.ClassificationConfig classification = new UseCaseDefinition.ClassificationConfig();
                classification.setKeywords(List.of("very", "specific", "keyword"));
                classification.setMinConfidence(5.0); // High threshold
                useCase.setClassification(classification);
                
                return List.of(useCase);
            }
        };
        
        RunbookClassifier testClassifier = new RunbookClassifier(testRegistry);
        String result = testClassifier.classify("very specific"); // Only 2 matches, below threshold
        assertEquals("UNKNOWN", result);
    }

    @Test
    void classify_withMinConfidence_aboveThreshold_returnsMatch() {
        RunbookRegistry testRegistry = new RunbookRegistry() {
            @Override
            public boolean isEnabled() {
                return true;
            }
            
            @Override
            public java.util.Collection<UseCaseDefinition> getAllUseCases() {
                UseCaseDefinition useCase = new UseCaseDefinition();
                UseCaseDefinition.UseCaseInfo info = new UseCaseDefinition.UseCaseInfo();
                info.setId("TEST_CASE");
                useCase.setUseCase(info);
                
                UseCaseDefinition.ClassificationConfig classification = new UseCaseDefinition.ClassificationConfig();
                classification.setKeywords(List.of("very", "specific", "keyword"));
                classification.setMinConfidence(2.0); // Low threshold
                useCase.setClassification(classification);
                
                return List.of(useCase);
            }
        };
        
        RunbookClassifier testClassifier = new RunbookClassifier(testRegistry);
        String result = testClassifier.classify("very specific keyword"); // 3 matches, above threshold
        assertEquals("TEST_CASE", result);
    }

    @Test
    void classify_withSynonyms_matchesCorrectly() {
        RunbookRegistry testRegistry = new RunbookRegistry() {
            @Override
            public boolean isEnabled() {
                return true;
            }
            
            @Override
            public java.util.Collection<UseCaseDefinition> getAllUseCases() {
                UseCaseDefinition useCase = new UseCaseDefinition();
                UseCaseDefinition.UseCaseInfo info = new UseCaseDefinition.UseCaseInfo();
                info.setId("TEST_CASE");
                useCase.setUseCase(info);
                
                UseCaseDefinition.ClassificationConfig classification = new UseCaseDefinition.ClassificationConfig();
                classification.setKeywords(List.of("cancel"));
                classification.setSynonyms(Map.of("cancel", List.of("delete", "abort")));
                useCase.setClassification(classification);
                
                return List.of(useCase);
            }
        };
        
        RunbookClassifier testClassifier = new RunbookClassifier(testRegistry);
        assertEquals("TEST_CASE", testClassifier.classify("delete"));
        assertEquals("TEST_CASE", testClassifier.classify("abort"));
    }

    @Test
    void classify_withNullKeywords_handlesGracefully() {
        RunbookRegistry testRegistry = new RunbookRegistry() {
            @Override
            public boolean isEnabled() {
                return true;
            }
            
            @Override
            public java.util.Collection<UseCaseDefinition> getAllUseCases() {
                UseCaseDefinition useCase = new UseCaseDefinition();
                UseCaseDefinition.UseCaseInfo info = new UseCaseDefinition.UseCaseInfo();
                info.setId("TEST_CASE");
                useCase.setUseCase(info);
                
                UseCaseDefinition.ClassificationConfig classification = new UseCaseDefinition.ClassificationConfig();
                classification.setKeywords(null);
                useCase.setClassification(classification);
                
                return List.of(useCase);
            }
        };
        
        RunbookClassifier testClassifier = new RunbookClassifier(testRegistry);
        String result = testClassifier.classify("any query");
        assertEquals("UNKNOWN", result);
    }

    @Test
    void classify_withNullSynonyms_handlesGracefully() {
        RunbookRegistry testRegistry = new RunbookRegistry() {
            @Override
            public boolean isEnabled() {
                return true;
            }
            
            @Override
            public java.util.Collection<UseCaseDefinition> getAllUseCases() {
                UseCaseDefinition useCase = new UseCaseDefinition();
                UseCaseDefinition.UseCaseInfo info = new UseCaseDefinition.UseCaseInfo();
                info.setId("TEST_CASE");
                useCase.setUseCase(info);
                
                UseCaseDefinition.ClassificationConfig classification = new UseCaseDefinition.ClassificationConfig();
                classification.setKeywords(List.of("test"));
                classification.setSynonyms(null);
                useCase.setClassification(classification);
                
                return List.of(useCase);
            }
        };
        
        RunbookClassifier testClassifier = new RunbookClassifier(testRegistry);
        String result = testClassifier.classify("test");
        assertEquals("TEST_CASE", result);
    }

    @Test
    void classify_selectsHighestScore() {
        RunbookRegistry testRegistry = new RunbookRegistry() {
            @Override
            public boolean isEnabled() {
                return true;
            }
            
            @Override
            public java.util.Collection<UseCaseDefinition> getAllUseCases() {
                UseCaseDefinition useCase1 = new UseCaseDefinition();
                UseCaseDefinition.UseCaseInfo info1 = new UseCaseDefinition.UseCaseInfo();
                info1.setId("LOW_SCORE");
                useCase1.setUseCase(info1);
                UseCaseDefinition.ClassificationConfig classification1 = new UseCaseDefinition.ClassificationConfig();
                classification1.setKeywords(List.of("test"));
                useCase1.setClassification(classification1);
                
                UseCaseDefinition useCase2 = new UseCaseDefinition();
                UseCaseDefinition.UseCaseInfo info2 = new UseCaseDefinition.UseCaseInfo();
                info2.setId("HIGH_SCORE");
                useCase2.setUseCase(info2);
                UseCaseDefinition.ClassificationConfig classification2 = new UseCaseDefinition.ClassificationConfig();
                classification2.setKeywords(List.of("test", "query", "match"));
                useCase2.setClassification(classification2);
                
                return List.of(useCase1, useCase2);
            }
        };
        
        RunbookClassifier testClassifier = new RunbookClassifier(testRegistry);
        String result = testClassifier.classify("test query match");
        assertEquals("HIGH_SCORE", result);
    }
}

