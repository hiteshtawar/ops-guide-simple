# Technical Requirements Document (TRD)
## Production Support - Operational Automation Assistant

**Version:** 1.0  
**Date:** November 6, 2025  
**Status:** Draft

---

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Solution Overview](#solution-overview)
3. [Technical Stack](#technical-stack)
4. [Architecture](#architecture)
5. [API Specifications](#api-specifications)
6. [Class Diagram](#class-diagram)
7. [Data Storage](#data-storage)
8. [Runbook Template](#runbook-template)
9. [Deployment](#deployment)
10. [Security Considerations](#security-considerations)

---

## 1. Problem Statement

### Current Challenges

**Manual Operations Overhead**
- Support engineers manually execute repetitive operational tasks (case cancellations, status updates, etc.)
- Each operation requires multiple API calls with specific payloads and validation steps
- High cognitive load remembering exact API endpoints, request formats, and validation rules
- Risk of human error in multi-step procedures

**Lack of Standardization**
- No centralized runbook system for operational procedures
- Knowledge scattered across wikis, Slack messages, and individual notes
- Inconsistent execution of operational tasks across team members
- Difficult to onboard new team members

**Time-Consuming Operations**
- Engineers spend 20-30% of their time on routine operational tasks
- Each case cancellation takes 5-10 minutes with manual API calls
- Status updates require looking up API specs and constructing correct payloads
- Pre-checks and post-checks often skipped due to time pressure

### Business Impact
- **Reduced Productivity:** Engineers spend time on manual tasks instead of feature development
- **Increased Risk:** Manual operations prone to errors, can cause data inconsistencies
- **Poor User Experience:** Delayed case updates and operations due to manual processes
- **Scalability Constraints:** Cannot scale operations team without linear increase in headcount

---

## 2. Solution Overview

### Overview
**Production Support** is a lightweight operational automation assistant that converts natural language queries into structured, validated API operations with built-in runbooks and safety checks.

### Key Features

**1. Natural Language Interface**
- Engineers describe what they want to do in plain English
- System classifies intent and extracts entities (case IDs, status values, etc.)
- No need to remember exact API syntax or endpoints

**2. Pattern-Based Classification**
- Simple, explainable keyword matching (no AI/ML overhead)
- Fast classification with minimal latency (<50ms)
- Easy to debug and extend with new patterns

**3. Runbook-Driven Execution**
- All operations follow documented runbooks with pre-checks, procedures, and post-checks
- Runbooks stored as markdown files for easy versioning and review
- Human-readable format that serves as both documentation and execution template

**4. Step-by-Step Guidance**
- Breaks complex operations into manageable steps
- Shows expected results and validation points
- Supports auto-execution for safe operations, manual review for critical ones

**5. Safety Features**
- Extracts and validates required entities before execution
- Provides warnings for critical operations
- Clear rollback procedures in runbooks

### User Workflow

```
User Input: "please cancel case 2025123P6732"
    ↓
Classification: CANCEL_CASE
Entity Extraction: {case_id: "2025123P6732"}
    ↓
Runbook Retrieval: cancel-case-runbook.md
    ↓
Step-by-Step Display:
  1. [Precheck] Verify case exists
  2. [Precheck] Check case is not already cancelled
  3. [Procedure] Call PATCH /api/cases/{case_id}
  4. [Postcheck] Verify status updated
    ↓
Execution: Auto-execute or manual confirmation
    ↓
Result: Success/Failure with details
```

### Success Metrics
- **Time Saved:** Reduce operational task time by 70% (from 10 min to 3 min per operation)
- **Error Reduction:** Decrease operational errors by 90% through standardized runbooks
- **Adoption:** 80% of support engineers using OpsGuide within 3 months
- **Coverage:** Support 10+ common operational patterns in MVP

---

## 3. Technical Stack

### Backend Framework
- **Language:** Java 17
- **Framework:** Spring Boot 3.2.0
- **Why:** Enterprise-grade, excellent ecosystem, team familiarity

### Core Dependencies
```xml
<dependencies>
    <!-- Web Framework -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- WebClient for downstream API calls -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    
    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- Utilities -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
    
    <!-- Monitoring -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

### Build & Packaging
- **Build Tool:** Maven 3.9+
- **Java Version:** 17 (LTS)
- **Packaging:** JAR with embedded Tomcat
- **Container:** Docker multi-stage build

### Infrastructure
- **Container Orchestration:** AWS ECS (Elastic Container Service)
- **API Gateway:** AWS API Gateway (xxx.apigtw.com)
- **Load Balancing:** Application Load Balancer (ALB)
- **Service Discovery:** AWS Cloud Map
- **Logging:** CloudWatch Logs
- **Metrics:** CloudWatch Metrics + Spring Boot Actuator

### External Dependencies
- **Case Management API:** Downstream service for case operations
- **No Database:** Stateless service, all data from downstream APIs
- **Runbooks:** Bundled in JAR as classpath resources

---

## 4. Architecture

### 4.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Internet / Users                          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ HTTPS
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                    API Gateway (xxx.apigtw.com)                  │
│  - Authentication (JWT/API Key)                                  │
│  - Rate Limiting                                                 │
│  - Request Validation                                            │
│  - CORS                                                          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ HTTPS
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                   Application Load Balancer (ALB)                │
│  - Health Checks                                                 │
│  - SSL Termination                                               │
│  - Target Group Management                                       │
└────────────────────────────┬────────────────────────────────────┘
                             │
                   ┌─────────┴─────────┐
                   │                   │
    ┌──────────────▼──────┐  ┌────────▼──────────────┐
    │   ECS Task 1        │  │   ECS Task 2          │
    │  (OpsGuide Service) │  │  (OpsGuide Service)   │
    │                     │  │                       │
    │  Port: 8093        │  │  Port: 8093           │
    └──────────┬──────────┘  └────────┬──────────────┘
               │                      │
               └──────────┬───────────┘
                          │
                          │ HTTPS
                          │
         ┌────────────────▼────────────────────┐
         │   Case Management API               │
         │   (Downstream Service)              │
         │   - GET /api/cases/{id}            │
         │   - PATCH /api/cases/{id}          │
         └─────────────────────────────────────┘
```

### 4.2 Component Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Production Support Service                    │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │               Controller Layer                              │ │
│  │  - ProductionSupportController: REST endpoints             │ │
│  │  - GlobalExceptionHandler: Error handling                  │ │
│  └──────────────────────────┬─────────────────────────────────┘ │
│                             │                                    │
│  ┌──────────────────────────▼─────────────────────────────────┐ │
│  │               Service Layer                                 │ │
│  │  - OpsGuideOrchestrator: Main orchestration logic          │ │
│  │  - PatternClassifier: Query classification                 │ │
│  │  - RunbookParser: Parse and retrieve runbooks              │ │
│  │  - StepExecutionService: Execute individual steps          │ │
│  └──────────────────────────┬─────────────────────────────────┘ │
│                             │                                    │
│  ┌──────────────────────────▼─────────────────────────────────┐ │
│  │               Model Layer                                   │ │
│  │  - OperationalRequest/Response                             │ │
│  │  - StepExecutionRequest/Response                           │ │
│  │  - RunbookStep                                             │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │               Configuration                                 │ │
│  │  - WebClientConfig: HTTP client setup                      │ │
│  │  - CorsConfig: CORS policies                              │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │               Resources                                     │ │
│  │  - runbooks/*.md: Operational runbooks                     │ │
│  │  - api-specs/*.md: API documentation                       │ │
│  │  - application.yml: Configuration                          │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 Request Flow

```
1. User Request
   POST /api/v1/process
   { "query": "cancel case 2025123P6732" }
         ↓
2. Controller (OpsGuideController)
   - Validates request (@Valid)
   - Logs request
         ↓
3. Orchestrator (OpsGuideOrchestrator)
   - Coordinates classification and runbook retrieval
         ↓
4. Classifier (PatternClassifier)
   - Normalizes query: "cancel case 2025123P6732"
   - Matches pattern: CANCEL_CASE
   - Extracts entities: {case_id: "2025123P6732"}
         ↓
5. Runbook Parser (RunbookParser)
   - Loads: cancel-case-runbook.md
   - Parses steps with metadata
   - Returns structured steps
         ↓
6. Response Builder
   - Assembles response with:
     * taskId: "CANCEL_CASE"
     * taskName: "Cancel Case"
     * extractedEntities: {...}
     * steps: [...]
     * warnings: [...]
         ↓
7. Return to User
   200 OK with structured response
```

---

## 5. API Specifications

### Base URL
```
Production: https://xxx.apigtw.com/production-support/api/v1
Development: http://localhost:8093/api/v1
```

### 5.1 Process Natural Language Query

**Endpoint:** `POST /api/v1/process`

**Description:** Main endpoint that accepts natural language queries, classifies them, and returns structured runbook steps.

**Request:**
```json
{
  "query": "please cancel case 2025123P6732",
  "userId": "engineer@example.com",
  "taskId": null  // Optional: override classification
}
```

**Response (200 OK):**
```json
{
  "taskId": "CANCEL_CASE",
  "taskName": "Cancel Case",
  "extractedEntities": {
    "case_id": "2025123P6732"
  },
  "steps": [
    {
      "stepNumber": 1,
      "description": "Verify case exists and get current status",
      "method": "GET",
      "path": "/api/cases/{case_id}",
      "requestBody": null,
      "expectedResponse": "200 OK with case details",
      "autoExecutable": true,
      "stepType": "precheck"
    },
    {
      "stepNumber": 2,
      "description": "Verify case is not already cancelled",
      "method": "VALIDATE",
      "path": null,
      "requestBody": null,
      "expectedResponse": "Current status should not be 'cancelled'",
      "autoExecutable": true,
      "stepType": "precheck"
    },
    {
      "stepNumber": 3,
      "description": "Cancel the case",
      "method": "PATCH",
      "path": "/api/cases/{case_id}",
      "requestBody": "{\"status\": \"cancelled\", \"reason\": \"Cancelled by support\"}",
      "expectedResponse": "200 OK",
      "autoExecutable": false,
      "stepType": "procedure"
    },
    {
      "stepNumber": 4,
      "description": "Verify case status updated to cancelled",
      "method": "GET",
      "path": "/api/cases/{case_id}",
      "requestBody": null,
      "expectedResponse": "Status should be 'cancelled'",
      "autoExecutable": true,
      "stepType": "postcheck"
    }
  ],
  "warnings": [
    "Case cancellation is a critical operation. Please review pre-checks carefully."
  ]
}
```

**Error Response (400 Bad Request):**
```json
{
  "timestamp": "2025-11-06T00:00:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Invalid request parameters",
  "errors": {
    "query": "Query is required and cannot be empty"
  }
}
```

### 5.2 Classify Query Only

**Endpoint:** `POST /api/v1/classify`

**Description:** Lightweight endpoint that only classifies the query without retrieving full runbook steps.

**Request:**
```json
{
  "query": "update case status to pending 2025123P6732"
}
```

**Response (200 OK):**
```json
{
  "taskId": "UPDATE_CASE_STATUS",
  "taskName": "Update Case Status",
  "extractedEntities": {
    "case_id": "2025123P6732",
    "status": "pending"
  },
  "steps": null,
  "warnings": []
}
```

### 5.3 Get Steps for Task

**Endpoint:** `GET /api/v1/tasks/{taskId}/steps`

**Query Parameters:**
- `stage` (optional): Filter by stage (precheck, procedure, postcheck, rollback)

**Example:** `GET /api/v1/tasks/CANCEL_CASE/steps?stage=precheck`

**Response (200 OK):**
```json
[
  {
    "stepNumber": 1,
    "description": "Verify case exists and get current status",
    "method": "GET",
    "path": "/api/cases/{case_id}",
    "stepType": "precheck",
    "autoExecutable": true
  },
  {
    "stepNumber": 2,
    "description": "Verify case is not already cancelled",
    "method": "VALIDATE",
    "stepType": "precheck",
    "autoExecutable": true
  }
]
```

### 5.4 Execute Step

**Endpoint:** `POST /api/v1/execute-step`

**Description:** Execute a specific step from a runbook.

**Request:**
```json
{
  "taskId": "CANCEL_CASE",
  "stepNumber": 3,
  "entities": {
    "case_id": "2025123P6732"
  },
  "userId": "engineer@example.com",
  "authToken": "Bearer eyJhbGc..."
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "stepNumber": 3,
  "message": "Step executed successfully",
  "apiResponse": {
    "status": 200,
    "body": "{\"id\": \"2025123P6732\", \"status\": \"cancelled\"}"
  },
  "executionTimeMs": 145,
  "nextStep": 4
}
```

**Error Response (500 Internal Server Error):**
```json
{
  "success": false,
  "stepNumber": 3,
  "message": "API call failed",
  "error": "404 Not Found: Case not found",
  "executionTimeMs": 89,
  "nextStep": null
}
```

### 5.5 Health Check

**Endpoint:** `GET /api/v1/health`

**Response (200 OK):**
```
OK
```

### 5.6 Spring Boot Actuator Endpoints

**Base:** `/actuator`

Available endpoints:
- `GET /actuator/health` - Service health status
- `GET /actuator/metrics` - Application metrics
- `GET /actuator/info` - Service information

---

## 6. Class Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Controller Layer                            │
└─────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────┐
│   ProductionSupportController    │
├──────────────────────────────────┤
│ - orchestrator: OpsGuideOrchestrator │
│ - stepExecutionService: StepExecutionService │
├──────────────────────────────────┤
│ + processRequest(OperationalRequest): ResponseEntity │
│ + classifyOnly(OperationalRequest): ResponseEntity   │
│ + getSteps(taskId, stage): ResponseEntity           │
│ + executeStep(StepExecutionRequest): ResponseEntity │
│ + health(): ResponseEntity                          │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│   GlobalExceptionHandler         │
├──────────────────────────────────┤
│ + handleValidationExceptions(MethodArgumentNotValidException): ResponseEntity │
│ + handleGenericException(Exception): ResponseEntity │
└──────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                         Service Layer                                │
└─────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────┐
│   OpsGuideOrchestrator           │
├──────────────────────────────────┤
│ - patternClassifier: PatternClassifier │
│ - runbookParser: RunbookParser         │
│ - TASK_NAMES: Map<String, String>     │
├──────────────────────────────────┤
│ + processRequest(OperationalRequest): OperationalResponse │
│ + getStepsForStage(taskId, stage): List<RunbookStep>    │
│ - buildWarnings(taskId, entities): List<String>          │
└──────────────────────────────────┘
                │
                │ uses
                ▼
┌──────────────────────────────────┐
│   PatternClassifier              │
├──────────────────────────────────┤
│ - CASE_ID_PATTERN: Pattern       │
│ - STATUS_PATTERN: Pattern        │
├──────────────────────────────────┤
│ + classify(query): ClassificationResult │
│ - normalizeQuery(query): String         │
│ - normalizeStatus(status): String       │
│ - classifyTask(query, entities): String │
│ - containsAny(query, keywords): boolean │
│ - containsStatus(query): boolean        │
└──────────────────────────────────┘
                │
                │ returns
                ▼
┌──────────────────────────────────┐
│   ClassificationResult           │
├──────────────────────────────────┤
│ - taskId: String                 │
│ - entities: Map<String, String>  │
├──────────────────────────────────┤
│ + getTaskId(): String           │
│ + getEntities(): Map            │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│   RunbookParser                  │
├──────────────────────────────────┤
│ - RUNBOOK_PATHS: Map<String, String> │
├──────────────────────────────────┤
│ + getSteps(taskId, stage): List<RunbookStep> │
│ - parseRunbook(content): List<RunbookStep>   │
│ - loadRunbookContent(path): String           │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│   StepExecutionService           │
├──────────────────────────────────┤
│ - webClient: WebClient           │
│ - runbookParser: RunbookParser   │
│ - caseManagementBaseUrl: String  │
├──────────────────────────────────┤
│ + executeStep(StepExecutionRequest): StepExecutionResponse │
│ - executeApiCall(step, entities, authToken): ApiResponse   │
│ - replaceEntities(template, entities): String              │
└──────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                         Model Layer                                  │
└─────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────┐
│   OperationalRequest             │
├──────────────────────────────────┤
│ - query: String [@NotBlank]      │
│ - userId: String                 │
│ - taskId: String                 │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│   OperationalResponse            │
├──────────────────────────────────┤
│ - taskId: String                 │
│ - taskName: String               │
│ - extractedEntities: Map         │
│ - steps: List<RunbookStep>       │
│ - warnings: List<String>         │
└──────────────────────────────────┘
                │
                │ contains
                ▼
┌──────────────────────────────────┐
│   RunbookStep                    │
├──────────────────────────────────┤
│ - stepNumber: Integer            │
│ - description: String            │
│ - method: String                 │
│ - path: String                   │
│ - requestBody: String            │
│ - expectedResponse: String       │
│ - autoExecutable: Boolean        │
│ - stepType: String               │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│   StepExecutionRequest           │
├──────────────────────────────────┤
│ - taskId: String [@NotBlank]     │
│ - stepNumber: Integer [@NotNull] │
│ - entities: Map<String, String>  │
│ - userId: String                 │
│ - authToken: String              │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│   StepExecutionResponse          │
├──────────────────────────────────┤
│ - success: Boolean               │
│ - stepNumber: Integer            │
│ - message: String                │
│ - apiResponse: Map               │
│ - error: String                  │
│ - executionTimeMs: Long          │
│ - nextStep: Integer              │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│   ValidationErrorResponse        │
├──────────────────────────────────┤
│ - timestamp: LocalDateTime       │
│ - status: int                    │
│ - error: String                  │
│ - message: String                │
│ - errors: Map<String, String>    │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│   ErrorResponse                  │
├──────────────────────────────────┤
│ - timestamp: LocalDateTime       │
│ - status: int                    │
│ - error: String                  │
│ - message: String                │
└──────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                         Configuration                                │
└─────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────┐
│   WebClientConfig                │
├──────────────────────────────────┤
│ + webClient(): WebClient         │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│   CorsConfig                     │
├──────────────────────────────────┤
│ + corsFilter(): CorsFilter       │
└──────────────────────────────────┘
```

---

## 7. Data Storage

### Current Approach: No Database

**Rationale:**
- OpsGuide is a **stateless orchestration service**
- All case data resides in downstream Case Management API
- Runbooks are **static resources** bundled in JAR
- No need for persistent storage in MVP

**Benefits:**
- Simplified deployment and operations
- Easy horizontal scaling (no shared state)
- Reduced infrastructure complexity
- Lower operational overhead

### Data Sources

**1. Runbooks (Embedded Resources)**
```
src/main/resources/runbooks/
├── cancel-case-runbook.md
└── update-case-status-runbook.md
```

**2. API Specifications (Embedded Resources)**
```
src/main/resources/api-specs/
└── case-management-api.md
```

**3. Case Data (External API)**
- Source: Case Management API
- Access: RESTful HTTP calls via WebClient
- No local caching in MVP

### Future Considerations (Post-MVP)

**If persistence becomes needed:**

**Potential Use Cases:**
- Execution history and audit logs
- User preferences and favorites
- Custom runbooks per team/user
- Performance metrics and analytics

**Technology Options:**
- **PostgreSQL:** Relational data, ACID guarantees
- **DynamoDB:** NoSQL, serverless, good for audit logs
- **S3 + CloudWatch:** For logs and analytics only

---

## 8. Runbook Template

### Markdown Structure

```markdown
# {Operation Name} Runbook

## Overview
Brief description of what this operation does and when to use it.

## Prerequisites
- Required permissions
- Required entities (case_id, status, etc.)
- Any environmental checks

## Pre-checks
Steps to verify before executing the main operation.

### Step 1: {Precheck Description}
- **Type:** precheck
- **Method:** GET
- **Path:** /api/cases/{case_id}
- **Expected:** 200 OK with case details
- **Auto-executable:** true

### Step 2: {Precheck Validation}
- **Type:** precheck
- **Method:** VALIDATE
- **Expected:** Current status should not be 'cancelled'
- **Auto-executable:** true

## Procedure
Main operation steps.

### Step 3: {Main Operation}
- **Type:** procedure
- **Method:** PATCH
- **Path:** /api/cases/{case_id}
- **Request Body:**
```json
{
  "status": "cancelled",
  "reason": "Cancelled by support"
}
```
- **Expected:** 200 OK
- **Auto-executable:** false
- **Notes:** Requires manual confirmation for critical operation

## Post-checks
Verification steps after the operation.

### Step 4: {Postcheck Verification}
- **Type:** postcheck
- **Method:** GET
- **Path:** /api/cases/{case_id}
- **Expected:** Status should be 'cancelled'
- **Auto-executable:** true

## Rollback
Steps to rollback if something goes wrong.

### Step 5: {Rollback Action}
- **Type:** rollback
- **Method:** PATCH
- **Path:** /api/cases/{case_id}
- **Request Body:**
```json
{
  "status": "{original_status}",
  "reason": "Rollback from failed cancellation"
}
```
- **Expected:** 200 OK
- **Auto-executable:** false

## Success Criteria
- Case status is 'cancelled'
- Audit log entry created
- No downstream errors

## Common Issues
### Issue: Case not found (404)
**Solution:** Verify case ID is correct and case exists in system

### Issue: Insufficient permissions (403)
**Solution:** Check user has CASE_CANCEL permission
```

### Example: Cancel Case Runbook

See: `src/main/resources/runbooks/cancel-case-runbook.md`

### Example: Update Case Status Runbook

See: `src/main/resources/runbooks/update-case-status-runbook.md`

### Runbook Metadata Format

Each step in the runbook follows this structure:

```yaml
stepNumber: 1
description: "Verify case exists and get current status"
method: "GET"  # HTTP method or VALIDATE for logical checks
path: "/api/cases/{case_id}"  # API endpoint with placeholders
requestBody: null  # JSON string if POST/PATCH/PUT
expectedResponse: "200 OK with case details"
autoExecutable: true  # Can be auto-executed vs requires manual review
stepType: "precheck"  # precheck, procedure, postcheck, rollback
```

---

## 9. Deployment

### 9.1 ECS Task Definition

```json
{
  "family": "ops-guide-simple",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "ops-guide-simple",
      "image": "xxxxx.dkr.ecr.us-east-1.amazonaws.com/ops-guide-simple:latest",
      "portMappings": [
        {
          "containerPort": 8093,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "prod"
        },
        {
          "name": "CASE_MANAGEMENT_API_BASE_URL",
          "value": "https://case-api.internal.example.com"
        }
      ],
      "secrets": [
        {
          "name": "API_KEY",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:xxxxx:secret:ops-guide/api-key"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/ops-guide-simple",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8093/api/v1/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ],
  "executionRoleArn": "arn:aws:iam::xxxxx:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::xxxxx:role/opsGuideTaskRole"
}
```

### 9.2 ECS Service Configuration

```json
{
  "serviceName": "ops-guide-simple-service",
  "cluster": "ops-tools-cluster",
  "taskDefinition": "ops-guide-simple:1",
  "desiredCount": 2,
  "launchType": "FARGATE",
  "platformVersion": "LATEST",
  "networkConfiguration": {
    "awsvpcConfiguration": {
      "subnets": [
        "subnet-xxxxx",
        "subnet-yyyyy"
      ],
      "securityGroups": [
        "sg-zzzzz"
      ],
      "assignPublicIp": "DISABLED"
    }
  },
  "loadBalancers": [
    {
      "targetGroupArn": "arn:aws:elasticloadbalancing:us-east-1:xxxxx:targetgroup/ops-guide-tg",
      "containerName": "ops-guide-simple",
      "containerPort": 8093
    }
  ],
  "healthCheckGracePeriodSeconds": 60,
  "deploymentConfiguration": {
    "maximumPercent": 200,
    "minimumHealthyPercent": 100
  }
}
```

### 9.3 API Gateway Integration

**Resource:** `/ops-guide/{proxy+}`

**Method:** `ANY`

**Integration Type:** HTTP Proxy

**Endpoint URL:** `http://internal-alb-xxxxx.us-east-1.elb.amazonaws.com/api/v1/{proxy}`

**Settings:**
```yaml
Authentication: AWS_IAM or Custom Authorizer (Lambda)
Rate Limiting: 1000 requests/second per API key
Throttling: 
  Burst: 2000
  Rate: 1000
CORS: Enabled
  - Allow Origins: https://internal-tools.example.com
  - Allow Methods: GET, POST, OPTIONS
  - Allow Headers: Content-Type, Authorization
Caching: Disabled (operational data)
Logging: Full request/response logging
Metrics: Enabled (CloudWatch)
```

### 9.4 Dockerfile

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/ops-guide-simple-*.jar app.jar

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Expose port
EXPOSE 8093

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8093/api/v1/health || exit 1

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 9.5 Environment Configuration

**Development (application-dev.yml):**
```yaml
server:
  port: 8093

case-management:
  api:
    base-url: https://dev-api.example.com/v2

logging:
  level:
    com.opsguide: DEBUG
```

**Production (application-prod.yml):**
```yaml
server:
  port: 8093

case-management:
  api:
    base-url: https://api.example.com/v2

logging:
  level:
    com.opsguide: INFO
    org.springframework: WARN
```

### 9.6 Auto-Scaling Configuration

```yaml
Target Tracking Scaling Policy:
  Metric: ECS Service Average CPU Utilization
  Target Value: 70%
  Scale-out cooldown: 60 seconds
  Scale-in cooldown: 300 seconds

Min tasks: 2
Max tasks: 10
```

---

## 10. Security Considerations

### 10.1 Authentication & Authorization

**API Gateway Level:**
- JWT token validation via Custom Authorizer (Lambda)
- API Key validation for service-to-service calls
- IAM authentication for internal AWS services

**Application Level:**
- Validate authToken in StepExecutionRequest
- Pass user credentials to downstream APIs
- No user credential storage

### 10.2 Input Validation

- Jakarta Bean Validation on all request models
- Query input sanitization before classification
- Entity extraction validation
- Prevent injection attacks in API calls

### 10.3 Secrets Management

- AWS Secrets Manager for API keys and tokens
- No hardcoded credentials
- Secrets injected as environment variables at runtime
- Automatic rotation support

### 10.4 Network Security

**VPC Configuration:**
- ECS tasks in private subnets
- No public IP assignment
- Security groups restrict traffic:
  - Inbound: Only from ALB on port 8093
  - Outbound: Only to Case Management API

**TLS/SSL:**
- TLS 1.2+ enforced at API Gateway
- Mutual TLS for internal service communication
- Certificate management via AWS Certificate Manager

### 10.5 Audit Logging

- All requests logged with:
  - Timestamp
  - User ID
  - Query/operation
  - Classification result
  - Execution outcome
- Logs sent to CloudWatch Logs
- Retention: 90 days
- No sensitive data (PII, credentials) in logs

### 10.6 Rate Limiting

**API Gateway:**
- 1000 requests/second per API key
- Burst: 2000 requests

**Application:**
- No application-level rate limiting in MVP
- Future: Redis-based rate limiting per user

---

## Appendices

### A. Supported Operations (MVP)

| Operation | Task ID | Description |
|-----------|---------|-------------|
| Cancel Case | CANCEL_CASE | Cancel a pathology case |
| Update Case Status | UPDATE_CASE_STATUS | Change case workflow status |

### B. Supported Case Status Values

- pending
- accessioning
- grossing
- embedding
- cutting
- staining
- microscopy
- under_review
- on_hold
- completed
- cancelled
- archived
- closed

### C. Dependencies

**External Services:**
- Case Management API (required)
- AWS Secrets Manager (required)
- CloudWatch (required)
- API Gateway (required)

**No Dependencies:**
- No database
- No cache (Redis/Memcached)
- No message queue
- No AI/ML services

### D. Performance Targets

- **Latency (p50):** < 200ms for classification
- **Latency (p99):** < 500ms for classification
- **Throughput:** Support 100 concurrent requests
- **Availability:** 99.9% uptime

### E. Monitoring & Alerts

**Key Metrics:**
- Request rate (requests/second)
- Error rate (errors/second)
- Latency (p50, p95, p99)
- ECS task CPU/memory utilization
- Downstream API failure rate

**Alerts:**
- Error rate > 5% for 5 minutes
- p99 latency > 1 second for 5 minutes
- ECS task crash loop
- Downstream API 5xx rate > 10%

---

**Document Control**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-11-06 | Engineering Team | Initial TRD |

---

**Approval**

| Role | Name | Signature | Date |
|------|------|-----------|------|
| Engineering Lead | | | |
| Product Manager | | | |
| DevOps Lead | | | |

