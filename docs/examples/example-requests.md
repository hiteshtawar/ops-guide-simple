# Example API Requests

## Example 1: Cancel a Case

### Request
```bash
curl -X POST http://localhost:8080/api/v1/process \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Cancel case CASE-2024-001",
    "userId": "ops123",
    "downstreamService": "ap-services"
  }'
```

### Response
```json
{
  "taskId": "CANCEL_CASE",
  "taskName": "Cancel Case",
  "downstreamService": "ap-services",
  "extractedEntities": {
    "case_id": "CASE-2024-001"
  },
  "confidence": 0.8,
  "warnings": [
    "Case cancellation is a critical operation. Please review pre-checks carefully."
  ],
  "steps": [
    {
      "stepNumber": 1,
      "description": "Verify user has case_admin or ops_engineer role",
      "method": "GET",
      "path": "/api/v2/users/{user_id}/roles",
      "requestBody": null,
      "expectedResponse": null,
      "autoExecutable": true,
      "stepType": "precheck"
    },
    {
      "stepNumber": 2,
      "description": "Case must be in cancellable state",
      "method": "GET",
      "path": "/api/v2/cases/{case_id}/status",
      "requestBody": null,
      "expectedResponse": null,
      "autoExecutable": true,
      "stepType": "precheck"
    }
  ]
}
```

## Example 2: Update Case Status

### Request
```bash
curl -X POST http://localhost:8080/api/v1/process \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Update case CASE-2024-005 status to completed",
    "userId": "pathologist456",
    "downstreamService": "ap-services"
  }'
```

### Response
```json
{
  "taskId": "UPDATE_CASE_STATUS",
  "taskName": "Update Case Status",
  "downstreamService": "ap-services",
  "extractedEntities": {
    "case_id": "CASE-2024-005",
    "status": "completed"
  },
  "confidence": 0.85,
  "warnings": [],
  "steps": [
    {
      "stepNumber": 1,
      "description": "Verify user has appropriate role for status transition",
      "method": "GET",
      "path": "/api/v2/users/{user_id}/roles",
      "autoExecutable": true,
      "stepType": "precheck"
    }
  ]
}
```

## Example 3: Get Specific Steps

### Get Pre-checks Only
```bash
curl http://localhost:8080/api/v1/tasks/CANCEL_CASE/steps?stage=precheck
```

### Get Procedure Steps
```bash
curl http://localhost:8080/api/v1/tasks/UPDATE_CASE_STATUS/steps?stage=procedure
```

## Example 4: Execute a Step

### Request
```bash
curl -X POST http://localhost:8080/api/v1/execute-step \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": "CANCEL_CASE",
    "downstreamService": "ap-services",
    "stepNumber": 1,
    "entities": {
      "case_id": "CASE-2024-001",
      "user_id": "ops123"
    },
    "userId": "ops123",
    "authToken": "your-jwt-token-here"
  }'
```

### Response (Success)
```json
{
  "success": true,
  "stepNumber": 1,
  "stepDescription": "Verify user roles",
  "statusCode": 200,
  "responseBody": "{\"user_id\":\"ops123\",\"roles\":[\"case_admin\",\"ops_engineer\"]}",
  "errorMessage": null,
  "durationMs": 245
}
```

### Response (Error)
```json
{
  "success": false,
  "stepNumber": 1,
  "stepDescription": "Verify user roles",
  "statusCode": null,
  "responseBody": null,
  "errorMessage": "Connection refused: api.example.com",
  "durationMs": 1523
}
```

## Example 5: Classification Only

### Request
```bash
curl -X POST http://localhost:8080/api/v1/classify \
  -H "Content-Type: application/json" \
  -d '{
    "query": "I need to cancel CASE-2024-999"
  }'
```

### Response
```json
{
  "taskId": "CANCEL_CASE",
  "taskName": "Cancel Case",
  "extractedEntities": {
    "case_id": "CASE-2024-999"
  },
  "confidence": 0.75,
  "warnings": [
    "Case cancellation is a critical operation. Please review pre-checks carefully."
  ],
  "steps": null
}
```

## Example 6: Various Query Formats

### Natural Language Variations

#### Cancel Case Queries
```json
{"query": "Cancel case CASE-2024-001"}
{"query": "I need to cancel CASE-2024-001"}
{"query": "Abort case CASE-2024-001"}
{"query": "Stop processing CASE-2024-001"}
{"query": "Terminate case CASE-2024-001"}
```

#### Update Status Queries
```json
{"query": "Update case CASE-2024-001 status to completed"}
{"query": "Mark CASE-2024-001 as completed"}
{"query": "Set case CASE-2024-001 to under_review"}
{"query": "Change status of CASE-2024-001 to pending"}
{"query": "Move CASE-2024-001 to staining"}
```

## Testing Workflow

### Full Workflow: Cancel a Case

1. **Classify and get runbook**
```bash
curl -X POST http://localhost:8080/api/v1/process \
  -H "Content-Type: application/json" \
  -d '{"query": "Cancel case CASE-2024-001", "userId": "ops123"}'
```

2. **Execute pre-check step 1** (Authorization)
```bash
curl -X POST http://localhost:8080/api/v1/execute-step \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": "CANCEL_CASE",
    "stepNumber": 1,
    "entities": {"case_id": "CASE-2024-001", "user_id": "ops123"},
    "userId": "ops123",
    "authToken": "your-jwt-token"
  }'
```

3. **Execute pre-check step 2** (Status validation)
```bash
curl -X POST http://localhost:8080/api/v1/execute-step \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": "CANCEL_CASE",
    "stepNumber": 2,
    "entities": {"case_id": "CASE-2024-001"},
    "userId": "ops123",
    "authToken": "your-jwt-token"
  }'
```

4. **Continue with procedure steps...**

## Health Check

```bash
curl http://localhost:8080/api/v1/health
```

Response: `OK`

