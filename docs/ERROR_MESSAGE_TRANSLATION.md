# Error Message Translation

## Overview

The execute-step API now translates technical error messages into user-friendly messages that non-engineers can understand. Technical details are preserved in the `responseBody` field for debugging purposes.

## Changes Made

### 1. Error Message Mapping Configuration

A new YAML configuration file (`error-messages.yaml`) defines mappings from technical errors to user-friendly messages:

```yaml
errorMappings:
  - pattern: "Connection refused.*:(\\d+)"
    userMessage: "Unable to connect to the downstream service. The service may be unavailable or not responding."
    category: "CONNECTION_ERROR"
  
  - pattern: ".*timeout.*"
    userMessage: "The operation took too long to complete and was cancelled. Please try again."
    category: "TIMEOUT_ERROR"
  
  # ... more patterns
```

### 2. Error Message Translator Service

The `ErrorMessageTranslator` service loads the YAML configuration and translates technical errors:

- Matches error messages against regex patterns
- Returns user-friendly messages
- Preserves technical details for debugging
- Categories errors for better handling

### 3. Updated Response Structure

When `success: false`, the response now includes:

- **`errorMessage`**: User-friendly message suitable for end users
- **`responseBody`**: Technical error details for engineers and debugging
- **`statusCode`**: HTTP status code (if applicable)

## Example Responses

### Before (Technical Error)

```json
{
  "success": false,
  "stepNumber": 3,
  "stepDescription": "Cancel the case in the system",
  "statusCode": null,
  "responseBody": null,
  "errorMessage": "Connection refused: localhost/127.0.0.1:8091",
  "durationMs": 44
}
```

### After (User-Friendly Error)

```json
{
  "success": false,
  "stepNumber": 3,
  "stepDescription": "Cancel the case in the system",
  "statusCode": null,
  "responseBody": "Connection refused: localhost/127.0.0.1:8091",
  "errorMessage": "Unable to connect to the downstream service. The service may be unavailable or not responding.",
  "durationMs": 44
}
```

## Supported Error Categories

### CONNECTION_ERROR
- Connection refused
- Connection timed out
- No route to host
- Unknown host

### TIMEOUT_ERROR
- Request timeout
- Operation timeout

### AUTH_ERROR
- 401 Unauthorized
- 403 Forbidden
- Access denied

### API_ERROR
- 400 Bad Request
- 404 Not Found
- 500 Internal Server Error
- 503 Service Unavailable

### CONFIG_ERROR
- Service not configured
- Step not found

### UNKNOWN_ERROR
- Any error that doesn't match known patterns

## Adding New Error Mappings

To add new error message mappings, simply edit the `error-messages.yaml` file:

```yaml
errorMappings:
  # Add your new mapping at the top for higher priority
  - pattern: "Your error pattern regex here"
    userMessage: "User-friendly message here"
    category: "ERROR_CATEGORY"
  
  # Existing mappings...
```

**Important Notes:**
- Patterns are checked in order - first match wins
- Place more specific patterns before general ones
- Use valid Java regex syntax for patterns
- Categories help with error tracking and metrics

## Testing

The `ErrorMessageTranslatorTest` class contains comprehensive tests for all error mappings. Run tests with:

```bash
mvn test -Dtest=ErrorMessageTranslatorTest
```

## Benefits

1. **Better User Experience**: Non-engineers can understand what went wrong
2. **Debugging Support**: Technical details still available in `responseBody`
3. **Maintainable**: Error mappings in YAML, easy to update without code changes
4. **Flexible**: Regex patterns support complex matching scenarios
5. **Categorized**: Errors grouped by category for better monitoring and alerting

