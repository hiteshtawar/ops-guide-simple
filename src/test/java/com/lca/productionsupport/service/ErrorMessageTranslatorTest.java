package com.lca.productionsupport.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for ErrorMessageTranslator
 */
class ErrorMessageTranslatorTest {

    private ErrorMessageTranslator translator;

    @BeforeEach
    void setUp() {
        translator = new ErrorMessageTranslator();
        translator.init();
    }

    @Test
    void testConnectionRefusedError() {
        String technicalError = "Connection refused: localhost/127.0.0.1:8091";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("Unable to connect to the downstream service. The service may be unavailable or not responding.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("CONNECTION_ERROR", result.getErrorCategory());
    }

    @Test
    void testTimeoutError() {
        String technicalError = "Request timeout after 5000ms";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("The operation took too long to complete and was cancelled. Please try again.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("TIMEOUT_ERROR", result.getErrorCategory());
    }

    @Test
    void testAuthenticationError() {
        String technicalError = "401 Unauthorized";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("Authentication failed. Please verify your credentials and try again.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("AUTH_ERROR", result.getErrorCategory());
    }

    @Test
    void testForbiddenError() {
        String technicalError = "403 Forbidden - Access denied";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("You do not have permission to perform this action.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("AUTH_ERROR", result.getErrorCategory());
    }

    @Test
    void testBadRequestError() {
        String technicalError = "API Error: 400 Bad Request - Invalid case_id";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("The request contains invalid data. Please check the input parameters.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("API_ERROR", result.getErrorCategory());
    }

    @Test
    void testNotFoundError() {
        String technicalError = "API Error: 404 Not Found";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("The requested resource was not found in the system.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("API_ERROR", result.getErrorCategory());
    }

    @Test
    void testInternalServerError() {
        String technicalError = "API Error: 500 Internal Server Error";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("The downstream service encountered an error. Please try again or contact support if the issue persists.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("API_ERROR", result.getErrorCategory());
    }

    @Test
    void testServiceUnavailableError() {
        String technicalError = "API Error: 503 Service Unavailable";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("The downstream service is temporarily unavailable. Please try again in a few moments.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("API_ERROR", result.getErrorCategory());
    }

    @Test
    void testConfigurationError() {
        String technicalError = "Downstream service not configured: invalid-service";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("The requested service is not properly configured. Please contact support.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("CONFIG_ERROR", result.getErrorCategory());
    }

    @Test
    void testStepNotFoundError() {
        String technicalError = "Step not found";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("The requested operation step was not found. Please verify the step number.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("CONFIG_ERROR", result.getErrorCategory());
    }

    @Test
    void testUnknownError() {
        String technicalError = "Some weird unexpected error that doesn't match any pattern";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("An unexpected error occurred while processing your request. Please try again or contact support if the issue persists.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("UNKNOWN_ERROR", result.getErrorCategory());
    }

    @Test
    void testNullError() {
        ErrorMessageTranslator.TranslationResult result = translator.translate(null);
        
        assertNotNull(result);
        assertEquals("An error occurred while processing your request.", 
                    result.getUserFriendlyMessage());
        assertNull(result.getTechnicalDetails());
        assertEquals("UNKNOWN_ERROR", result.getErrorCategory());
    }

    @Test
    void testEmptyError() {
        ErrorMessageTranslator.TranslationResult result = translator.translate("");
        
        assertNotNull(result);
        assertEquals("An error occurred while processing your request.", 
                    result.getUserFriendlyMessage());
        assertNull(result.getTechnicalDetails());
        assertEquals("UNKNOWN_ERROR", result.getErrorCategory());
    }

    @Test
    void testUnknownHostError() {
        String technicalError = "UnknownHostException: service.example.com";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("Unable to resolve the service address. The service may be misconfigured.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("CONNECTION_ERROR", result.getErrorCategory());
    }

    @Test
    void testConnectionTimedOutError() {
        String technicalError = "Connection timed out after waiting 30 seconds";
        
        ErrorMessageTranslator.TranslationResult result = translator.translate(technicalError);
        
        assertNotNull(result);
        assertEquals("The connection to the downstream service timed out. Please try again later.", 
                    result.getUserFriendlyMessage());
        assertEquals(technicalError, result.getTechnicalDetails());
        assertEquals("CONNECTION_ERROR", result.getErrorCategory());
    }
}

