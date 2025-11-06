# OpsGuide Simple - Architecture

## Overview

OpsGuide Simple is a pattern-matching based operational guide system. It's designed as an MVP that focuses on simplicity and practicality without complex AI/ML dependencies.

## Design Principles

1. **Simplicity First**: Use straightforward keyword/regex matching instead of AI
2. **No External Dependencies**: No embeddings, vector databases, or LLM services
3. **Runbook-Driven**: All operational knowledge stored in markdown runbooks
4. **API-First**: RESTful API for easy integration
5. **Extensible**: Easy to add new runbooks and patterns

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                         Client Layer                         │
│  (cURL, Postman, Web UI, CLI, Integration Scripts)          │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            │ HTTP/REST
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                    REST API Layer                            │
│                  OpsGuideController                          │
│  ┌──────────────┬─────────────┬───────────────────────┐    │
│  │ /process     │ /classify   │ /execute-step         │    │
│  │ /health      │ /tasks/{id} │                       │    │
│  └──────────────┴─────────────┴───────────────────────┘    │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│                   Orchestration Layer                        │
│                 OpsGuideOrchestrator                         │
│  - Coordinates classification and runbook retrieval          │
│  - Manages request/response flow                            │
│  - Builds warnings and validation                           │
└───────────────┬───────────────────────┬─────────────────────┘
                │                       │
    ┌───────────▼────────┐   ┌─────────▼──────────┐
    │ PatternClassifier  │   │  RunbookParser     │
    │                    │   │                    │
    │ - Keyword matching │   │ - Parse markdown   │
    │ - Regex patterns   │   │ - Extract steps    │
    │ - Entity extract   │   │ - Cache runbooks   │
    │ - Confidence calc  │   │                    │
    └────────────────────┘   └────────────────────┘
                │
┌───────────────▼─────────────────────────────────────────────┐
│               Step Execution Layer                           │
│             StepExecutionService                             │
│  - HTTP client (WebClient)                                  │
│  - Placeholder resolution                                   │
│  - Request/response handling                                │
│  - Error management                                         │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            │ HTTP/REST
                            │
┌───────────────────────────▼─────────────────────────────────┐
│              External Services Layer                         │
│         Case Management API (api.example.com/v2)            │
│  - GET /cases/{id}/status                                   │
│  - PATCH /cases/{id}/status                                 │
│  - POST /cases/{id}/cancel                                  │
│  - etc.                                                     │
└─────────────────────────────────────────────────────────────┘
```

## Component Details

### 1. REST API Layer (`OpsGuideController`)

**Responsibilities:**
- Expose HTTP endpoints
- Validate incoming requests
- Format responses
- Handle HTTP status codes

**Endpoints:**
- `POST /api/v1/process` - Main entry point
- `POST /api/v1/classify` - Classification only
- `GET /api/v1/tasks/{taskId}/steps` - Get runbook steps
- `POST /api/v1/execute-step` - Execute a step
- `GET /api/v1/health` - Health check

### 2. Orchestration Layer (`OpsGuideOrchestrator`)

**Responsibilities:**
- Coordinate between classifier and runbook parser
- Build complete operational responses
- Generate warnings and validation messages
- Handle explicit vs. implicit task ID routing

**Key Methods:**
- `processRequest()` - Main orchestration
- `getStepsForStage()` - Filter steps by stage
- `buildWarnings()` - Generate contextual warnings

### 3. Pattern Classification (`PatternClassifier`)

**Responsibilities:**
- Classify user queries into task IDs
- Extract entities (case IDs, statuses)
- Calculate confidence scores
- Pure pattern matching (no ML)

**Patterns:**
```java
// Cancel Case
Keywords: ["cancel", "abort", "terminate", "stop case"]
Entities: case_id

// Update Case Status
Keywords: ["update status", "change status", "set status", "mark as"]
Entities: case_id, status
```

**Entity Extraction:**
```regex
Case ID:  (?i)\b(CASE[-_]?\d{4}[-_]?\d+|\d{4}[-_]\d+)\b
Status:   (?i)\b(pending|completed|cancelled|...)\b
```

### 4. Runbook Parser (`RunbookParser`)

**Responsibilities:**
- Parse markdown runbook files
- Extract structured steps
- Cache runbooks in memory
- Identify API calls and metadata

**Parsing Logic:**
1. Identify sections (Pre-checks, Procedure, Post-checks, Rollback)
2. Extract code blocks (HTTP calls)
3. Parse HTTP method and path
4. Extract request bodies (JSON)
5. Determine auto-executability

**Step Structure:**
```json
{
  "stepNumber": 1,
  "description": "Authorization Check",
  "method": "GET",
  "path": "/api/v2/users/{user_id}/roles",
  "requestBody": null,
  "autoExecutable": true,
  "stepType": "precheck"
}
```

### 5. Step Execution (`StepExecutionService`)

**Responsibilities:**
- Execute HTTP requests to external APIs
- Replace placeholders in paths/bodies
- Add required headers (auth, idempotency)
- Handle timeouts and errors
- Return structured responses

**Request Flow:**
1. Get step definition from parser
2. Resolve placeholders: `{case_id}` → `CASE-2024-001`
3. Build HTTP request with headers
4. Execute and capture response
5. Return success/failure with timing

## Data Flow

### Process Request Flow

```
User Query: "Cancel case CASE-2024-001"
     │
     ▼
[PatternClassifier]
     │ Keywords: "cancel", "case"
     │ Entities: case_id = "CASE-2024-001"
     ▼
TaskID: CANCEL_CASE, Confidence: 0.8
     │
     ▼
[RunbookParser]
     │ Load: cancel-case-runbook.md
     │ Parse: Sections and steps
     ▼
Steps: [
  {stepNumber: 1, type: "precheck", method: "GET", ...},
  {stepNumber: 2, type: "precheck", method: "GET", ...},
  ...
]
     │
     ▼
[OpsGuideOrchestrator]
     │ Combine: classification + steps
     │ Build: warnings
     ▼
Response: {
  taskId, taskName, entities, steps, confidence, warnings
}
```

### Execute Step Flow

```
StepExecutionRequest
     │ taskId: CANCEL_CASE
     │ stepNumber: 1
     │ entities: {case_id: "CASE-2024-001"}
     ▼
[RunbookParser]
     │ Get step definition
     ▼
Step: {
  method: "GET",
  path: "/api/v2/users/{user_id}/roles"
}
     │
     ▼
[StepExecutionService]
     │ Resolve: /api/v2/users/ops123/roles
     │ Add headers: Authorization, X-User-ID
     │ Execute: HTTP GET
     ▼
External API Response
     │ Status: 200
     │ Body: {"roles": ["case_admin"]}
     ▼
StepExecutionResponse
     │ success: true
     │ statusCode: 200
     │ responseBody: ...
     │ durationMs: 245
     ▼
Return to client
```

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Java**: 17+
- **HTTP Client**: Spring WebFlux WebClient
- **Build Tool**: Maven
- **Runtime**: JVM (JRE 17+)

**Key Dependencies:**
- `spring-boot-starter-web` - REST API
- `spring-boot-starter-webflux` - HTTP client
- `lombok` - Reduce boilerplate
- `spring-boot-starter-actuator` - Health checks

**No Dependencies On:**
- AI/LLM services
- Vector databases
- Embedding models
- Apache Lucene/Solr/Elasticsearch
- Machine learning libraries

## Configuration

**application.yml:**
```yaml
case-management:
  api:
    base-url: https://api.example.com/v2

server:
  port: 8080
```

**Profiles:**
- `default` - Default configuration
- `dev` - Development (debug logging)
- `prod` - Production (minimal logging)

## Scalability Considerations

### Current MVP:
- Single instance
- In-memory runbook cache
- Synchronous processing
- No distributed coordination

### Future Scaling:
- Horizontal scaling (stateless)
- External cache (Redis) for runbooks
- Async step execution
- Rate limiting per user
- Circuit breakers for external APIs

## Security Considerations

### Current:
- Token passthrough to downstream APIs
- Basic input validation
- No authentication on OpsGuide endpoints

### Production Ready:
- Add Spring Security
- Validate JWT tokens
- Role-based access control
- Rate limiting
- Input sanitization
- Audit logging

## Extension Points

### Adding New Runbooks:
1. Create markdown file in `resources/runbooks/`
2. Register in `RunbookParser` constructor
3. Add patterns in `PatternClassifier`
4. Update `TASK_NAMES` map

### Adding New Patterns:
1. Add keywords in `PatternClassifier.classify()`
2. Create regex for entity extraction
3. Update confidence calculation

### Adding New Endpoints:
1. Add method in `OpsGuideController`
2. Wire to orchestrator or services
3. Document in README

## Testing Strategy

### Unit Tests:
- PatternClassifier: Pattern matching logic
- RunbookParser: Markdown parsing
- Entity extraction: Regex patterns

### Integration Tests:
- Controller endpoints
- Step execution with mocked external API
- End-to-end request flow

### Manual Testing:
- cURL examples in docs
- Postman collection
- Real API integration (dev environment)

## Monitoring & Observability

### Current:
- Spring Boot Actuator health endpoint
- Console logging (SLF4J)

### Production Ready:
- Structured logging (JSON)
- Metrics (Prometheus)
- Distributed tracing (Jaeger)
- Error tracking (Sentry)
- Dashboard (Grafana)

## Future Enhancements (Post-MVP)

1. **More Runbooks**: Add 10+ common operational tasks
2. **Better Pattern Matching**: NER, fuzzy matching
3. **Workflow Engine**: Multi-step orchestration
4. **Web UI**: Visual runbook navigation
5. **Audit Trail**: Complete execution history
6. **Rollback Support**: Automated rollback execution
7. **Scheduled Tasks**: Cron-like scheduling
8. **Integration Plugins**: Slack, PagerDuty, JIRA
9. **(Optional) AI Enhancement**: If needed, add LLM for better understanding

## Comparison: Simple vs. Full Version

| Feature | Simple (MVP) | Full Version |
|---------|-------------|--------------|
| Classification | Pattern matching | AI + Embeddings |
| Entity Extraction | Regex | NER + LLM |
| Runbook Storage | Markdown files | Vector database |
| Search | Keyword match | Semantic search |
| Confidence | Rule-based | ML-based |
| Setup Complexity | Low | High |
| Dependencies | Minimal | Many |
| Response Time | <100ms | 300-500ms |

## Conclusion

OpsGuide Simple achieves 80% of the value with 20% of the complexity. It's production-ready for teams that need practical operational guidance without the overhead of AI infrastructure.

