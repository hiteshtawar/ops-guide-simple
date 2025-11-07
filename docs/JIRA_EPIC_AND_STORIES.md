# JIRA Epic and Stories
## Production Support - Operational Automation System

**Created:** November 6, 2025  
**Product Owner:** [TBD]  
**Tech Lead:** [TBD]

---

## Epic

### Epic ID: LIMS2-100
### Epic Name: Production Support Functionality for Users

**Epic Description:**

Build an operational automation assistant that helps support team execute production tasks faster and safer. The system should accept natural language queries, classify intents, extract entities, and guide the team through validated runbooks with step-by-step execution.

**Business Value:**
- **Time Savings:** Reduce operational task time by 80% - from several days of waiting for engineering support to 3 minutes of self-service execution
- **Quality Improvement:** Decrease operational errors by 90% through standardized runbooks and API-based operations (vs direct database manipulation)
- **Team Empowerment:** Enable 50% of operational requests to be executed independently by support team without engineering intervention
- **Scalability:** Support growing case volume without proportional increase in engineering support tickets

**Target Release:** Q4 2025

**Epic Owner:** Engineering Team

---

## Stories

---

### Story 1: Repository Setup and Project Initialization

**Story ID:** LIMS2-101  
**Story Points:** 2  
**Priority:** Highest  
**Dependencies:** None

**Description:**

As a **DevOps Engineer**, I want to set up the Git repository with proper structure and build configuration, so that the development team can start building the Production Support system.

**Technical Details:**
- Create GitHub repository: `production-support-admin`
- Initialize Maven project with Java 17
- Set up Spring Boot 3.2.0 with required dependencies
- Configure multi-profile support (local, dev, prod)
- Add .gitignore and README

**Acceptance Criteria:**

✅ Repository created at `https://github.com/[org]/production-support-admin`  
✅ Maven project builds successfully with `mvn clean package`  
✅ Spring Boot application starts on port 8093  
✅ Health endpoint responds: `GET /api/v1/health` returns "OK"  
✅ README.md includes project overview and quick start  
✅ pom.xml includes all required dependencies (Web, WebFlux, Validation, Lombok, Actuator)  
✅ Three profiles configured: local, dev, prod  

**Definition of Done:**
- Code reviewed and merged to main
- Build passes in CI/CD
- Documentation complete

---

### Story 2: Infrastructure Provisioning - AWS ECS Setup

**Story ID:** LIMS2-102  
**Story Points:** 5  
**Priority:** High  
**Dependencies:** LIMS2-101

**Description:**

As a **DevOps Engineer**, I want to provision AWS infrastructure for Production Support service, so that the application can be deployed to production behind API Gateway.

**Technical Details:**
- Set up ECS Cluster: `production-support-cluster`
- Create ECS Task Definition for Fargate
- Configure Application Load Balancer (internal)
- Set up API Gateway integration at `lims.labcorp.com`
- Configure CloudWatch logging and metrics
- Set up auto-scaling policies

**Acceptance Criteria:**

✅ ECS cluster `production-support-cluster` created  
✅ Task definition supports 2 tasks minimum (512 CPU, 1024 MB memory)  
✅ ALB health checks pass for `/api/v1/health`  
✅ API Gateway resource `/production-support-admin/{proxy+}` configured  
✅ CloudWatch log group `/ecs/production-support-admin` created with 90-day retention  
✅ Auto-scaling configured (min: 2, max: 10, target CPU: 70%)  
✅ Security groups allow traffic: API Gateway → ALB → ECS tasks  
✅ ECS tasks run in private subnets (no public IP)  
✅ SSL termination at API Gateway  
✅ Deployment guide documented  

**Definition of Done:**
- Infrastructure provisioned in dev and prod environments
- Health checks passing
- Documentation updated with AWS resource ARNs

---

### Story 3: Natural Language Classification and Entity Extraction

**Story ID:** LIMS2-103  
**Story Points:** 8  
**Priority:** High  
**Dependencies:** LIMS2-101

**Description:**

As a **Backend Engineer**, I want to build a pattern-based classifier that parses natural language queries and extracts entities, so that support team queries can be automatically converted into structured operations.

**Technical Details:**
- Implement `PatternClassifier` service with keyword/regex matching
- Support query normalization (remove polite words, handle typos)
- Extract entities: case IDs (multiple formats), status values
- Create `TaskType` enum (CANCEL_CASE, UPDATE_CASE_STATUS, UNKNOWN)
- Support synonyms (cancel/delete/remove, update/change/set)

**Acceptance Criteria:**

✅ Classifies "cancel case 2025123P6732" as CANCEL_CASE  
✅ Classifies "update case status to pending 2024123P6731" as UPDATE_CASE_STATUS  
✅ Handles polite phrases: "please", "can you", "kindly"  
✅ Extracts case IDs from formats: 2025123P6732, CASE-2024-001  
✅ Extracts status values: pending, accessioning, grossing, etc.  
✅ Normalizes typos: "Acessioning" → "accessioning"  
✅ Returns UNKNOWN for unrecognized queries  
✅ Query normalization tested with 20+ variations  
✅ Entity extraction accuracy > 95%  
✅ Classification latency < 50ms  

**Definition of Done:**
- Unit tests cover 90%+ of patterns
- Pattern matching examples documented
- Code reviewed and merged

---

### Story 4: Cancel Case Runbook and API Integration

**Story ID:** LIMS2-104  
**Story Points:** 5  
**Priority:** High  
**Dependencies:** LIMS2-103

**Description:**

As a **Backend Engineer**, I want to implement the Cancel Case operation with runbook-driven execution, so that support team can safely cancel pathology cases with proper pre-checks and post-checks.

**Technical Details:**
- Create `cancel-case-runbook.md` with steps (precheck, procedure, postcheck, rollback)
- Implement `RunbookParser` to parse markdown and extract steps
- Build runbook with pre-checks (verify case exists, check not cancelled)
- Integrate with Case Management API: `PATCH /api/cases/{case_id}`
- Add warnings for critical operations

**Acceptance Criteria:**

✅ Runbook created with minimum 4 steps (2 prechecks, 1 procedure, 1 postcheck)  
✅ Pre-check 1: Verify case exists via `GET /api/cases/{case_id}`  
✅ Pre-check 2: Validate case is not already cancelled  
✅ Procedure: Cancel case via `PATCH /api/cases/{case_id}` with status=cancelled  
✅ Post-check: Verify case status updated to cancelled  
✅ Rollback procedure documented  
✅ RunbookParser successfully parses markdown and extracts structured steps  
✅ API endpoint `POST /api/v1/process` returns runbook for "cancel case X"  
✅ Warning message shown: "Case cancellation is a critical operation"  
✅ Steps marked as autoExecutable or manual approval required  

**Definition of Done:**
- Integration tests pass with mock Case Management API
- Runbook reviewed by Product/Support team
- Code merged to main

---

### Story 5: Update Case Status Runbook and API Integration

**Story ID:** LIMS2-105  
**Story Points:** 5  
**Priority:** High  
**Dependencies:** LIMS2-103

**Description:**

As a **Backend Engineer**, I want to implement the Update Case Status operation with runbook execution, so that support team can update case workflow statuses safely.

**Technical Details:**
- Create `update-case-status-runbook.md` with validation steps
- Support all status values: pending, accessioning, grossing, etc.
- Integrate with Case Management API for status updates
- Validate status transitions
- Extract both case_id and target status from query

**Acceptance Criteria:**

✅ Runbook created with minimum 4 steps  
✅ Pre-check: Verify case exists and get current status  
✅ Pre-check: Validate target status is valid  
✅ Procedure: Update status via `PATCH /api/cases/{case_id}`  
✅ Post-check: Verify status updated successfully  
✅ Supports actual case status values (pending, accessioning, grossing, etc.)  
✅ Extracts both case_id and status from query  
✅ Warning shown if status is not extracted from query  
✅ API endpoint returns runbook for "update status to X for case Y"  
✅ Handles variations: "mark as", "set status", "change to"  

**Definition of Done:**
- Integration tests cover all status transitions
- Edge cases tested (invalid status, missing entities)
- Code reviewed and merged

---

### Story 6: Fallback UI for Undetected Query Patterns

**Story ID:** LIMS2-106  
**Story Points:** 3  
**Priority:** Medium  
**Dependencies:** LIMS2-103, LIMS2-104, LIMS2-105

**Description:**

As a **Backend Engineer**, I want to provide an API endpoint that returns available task types, so that the UI can show manual selection when pattern detection fails.

**Technical Details:**
- Create `GET /api/v1/tasks` endpoint
- Return list of available tasks with descriptions
- Update classification to preserve original query
- Support manual task override via `taskId` parameter
- Entity extraction should still work with manual selection

**Acceptance Criteria:**

✅ Endpoint `GET /api/v1/tasks` returns all available tasks (excluding UNKNOWN)  
✅ Response includes: taskId, taskName, description  
✅ Description shows clear example: "Type: cancel case 2025P1234 and hit Send"  
✅ When taskId provided in request, classification is skipped  
✅ Entity extraction still runs from original query  
✅ Endpoint accessible without special permissions  
✅ Response cached appropriately  
✅ Documentation updated with fallback flow  

**Definition of Done:**
- API tested with manual task selection
- UI_FALLBACK_FLOW.md documented
- Swagger documentation updated

---

### Story 7: Step Execution Service and Downstream API Integration

**Story ID:** LIMS2-107  
**Story Points:** 8  
**Priority:** High  
**Dependencies:** LIMS2-104, LIMS2-105

**Description:**

As a **Backend Engineer**, I want to implement step execution that makes actual API calls to downstream services, so that runbook steps can be executed automatically or with approval.

**Technical Details:**
- Implement `StepExecutionService` with WebClient
- Support HTTP methods: GET, POST, PATCH, PUT, DELETE
- Handle entity placeholder replacement in URLs and request bodies
- Forward authentication tokens to downstream APIs
- Implement error handling and retry logic
- Track execution time and results

**Acceptance Criteria:**

✅ Endpoint `POST /api/v1/execute-step` accepts stepNumber and entities  
✅ Supports all HTTP methods (GET, POST, PATCH, PUT, DELETE)  
✅ Replaces placeholders: `{case_id}` → actual case ID from entities  
✅ Forwards Authorization header to downstream API  
✅ Returns success/failure with HTTP status code and response body  
✅ Execution time tracked in milliseconds  
✅ Handles 4xx and 5xx errors gracefully  
✅ Response includes: success, statusCode, responseBody, errorMessage, durationMs  
✅ WebClient configured with timeouts (5s connect, 30s read)  
✅ Base URL configurable via application.yml  

**Definition of Done:**
- Integration tests with mock downstream API
- Error scenarios tested (timeout, 404, 500)
- Logging includes request/response details

---

### Story 8: JWT Authentication and Authorization

**Story ID:** LIMS2-108  
**Story Points:** 5  
**Priority:** High  
**Dependencies:** LIMS2-101

**Description:**

As a **Backend Engineer**, I want to implement JWT-based authentication with role-based authorization, so that only authorized users can execute production operations.

**Technical Details:**
- Add Spring Security with OAuth2 Resource Server
- Implement @PreAuthorize on critical endpoints
- Support roles: `production_support`, `support_admin`
- Extract roles from JWT token (no database)
- Configure different security for dev/local/prod profiles

**Acceptance Criteria:**

✅ Spring Security dependency added  
✅ JWT decoder configured for HS256 (dev) and RS256 (prod)  
✅ Roles extracted from `roles` claim in JWT token  
✅ @PreAuthorize checks role on `/api/v1/process` and `/api/v1/execute-step`  
✅ Returns 401 if JWT missing or invalid  
✅ Returns 403 if user lacks required role  
✅ Local profile: security disabled for development  
✅ Dev profile: JWT required with test decoder  
✅ Prod profile: JWT required with production validation  
✅ Test JWT token provided (valid until 2035)  
✅ SECURITY_SETUP.md documented with architecture diagram  

**Definition of Done:**
- Security tested with valid/invalid/missing tokens
- Role-based access control verified
- Documentation includes JWT format and examples

---

### Story 9: OpenAPI/Swagger Documentation

**Story ID:** LIMS2-109  
**Story Points:** 3  
**Priority:** Medium  
**Dependencies:** LIMS2-103, LIMS2-104, LIMS2-105, LIMS2-107

**Description:**

As a **Backend Engineer**, I want to add comprehensive API documentation with Swagger/OpenAPI, so that frontend engineers and API consumers can understand and integrate with the endpoints.

**Technical Details:**
- Add springdoc-openapi dependency
- Annotate all endpoints with @Operation, @ApiResponse
- Add @Parameter descriptions for path/query parameters
- Configure Swagger UI
- Add API examples and schemas

**Acceptance Criteria:**

✅ Swagger UI accessible at `/swagger-ui.html`  
✅ OpenAPI spec available at `/api-docs`  
✅ All endpoints documented with:  
  - Summary and description  
  - Request/response examples  
  - Response codes (200, 400, 403, 500)  
  - Parameter descriptions  
✅ Request/response schemas generated from models  
✅ "Try it out" feature works in Swagger UI  
✅ API documentation includes authentication requirements  
✅ OpenAPIConfig sets service title, version, contact info  

**Definition of Done:**
- Swagger UI reviewed and approved
- API documentation complete
- Examples tested via Swagger UI

---

### Story 10: Frontend UI - Query Input and Classification Display

**Story ID:** PROD-110  
**Story Points:** 5  
**Priority:** High  
**Dependencies:** LIMS2-103, LIMS2-109

**Description:**

As a **Frontend Engineer**, I want to build a UI that accepts natural language queries and displays classification results, so that users can interact with the Production Support system.

**Technical Details:**
- Build input component with auto-expanding textarea
- Integrate with `POST /api/v1/process` endpoint
- Display classification results (taskId, taskName, extracted entities)
- Show warnings if any
- Handle loading and error states

**Acceptance Criteria:**

✅ Textarea accepts natural language input  
✅ Submit on Enter (Shift+Enter for new line)  
✅ Shows loading spinner while processing  
✅ Displays classification results in cards  
✅ Shows extracted entities (case_id, status)  
✅ Displays warnings with ⚠️ icon  
✅ Error messages shown for API failures  
✅ Input cleared after successful submission  
✅ JWT token included in Authorization header  
✅ CORS works with backend (localhost:8093)  
✅ Responsive design (mobile, tablet, desktop)  

**Definition of Done:**
- UI tested on Chrome, Safari, Firefox
- Accessibility requirements met (keyboard navigation, ARIA labels)
- Code reviewed and merged

---

### Story 11: Frontend UI - Task Selector Fallback

**Story ID:** PROD-111  
**Story Points:** 3  
**Priority:** Medium  
**Dependencies:** LIMS2-106, PROD-110

**Description:**

As a **Frontend Engineer**, I want to display available task options when pattern detection fails, so that users can manually select the correct operation.

**Technical Details:**
- Fetch available tasks from `GET /api/v1/tasks`
- Show task selector when classification returns UNKNOWN
- Display task descriptions with examples
- Resubmit with selected taskId and original query

**Acceptance Criteria:**

✅ Fetches available tasks on component mount  
✅ Shows task selector modal when taskId is UNKNOWN  
✅ Displays original query in selector  
✅ Shows clickable task cards with:  
  - Task name  
  - Description with example query  
✅ Clicking task resubmits with taskId parameter  
✅ Entity extraction still works from original query  
✅ Dismiss button closes selector  
✅ Smooth transitions and animations  
✅ Hover effects on task cards  

**Definition of Done:**
- Tested with vague queries that return UNKNOWN
- UX reviewed and approved
- Documentation includes screenshots

---

### Story 12: Frontend UI - Runbook Step Display and Execution

**Story ID:** PROD-112  
**Story Points:** 8  
**Priority:** High  
**Dependencies:** LIMS2-107, PROD-110

**Description:**

As a **Frontend Engineer**, I want to display runbook steps with execution controls, so that users can execute operations step-by-step with visual feedback.

**Technical Details:**
- Display steps from classification response
- Color-code by type (precheck, procedure, postcheck, rollback)
- Show step status (pending, running, completed, failed)
- Auto-execute eligible steps
- Manual approval for critical steps
- Integrate with `POST /api/v1/execute-step` endpoint

**Acceptance Criteria:**

✅ All steps displayed from backend response  
✅ Step cards show:  
  - Step number  
  - Description  
  - HTTP method and path  
  - Step type badge (color-coded)  
  - Status indicator  
✅ Auto-execute steps marked as `autoExecutable: true`  
✅ Manual steps show "Approve & Run" button  
✅ Step execution chain stops if step fails  
✅ Real-time status updates (pending → running → completed/failed)  
✅ Success/error messages displayed below each step  
✅ Execution results show API response details  
✅ Only one step executes at a time  
✅ Loading state shown while step executes  

**Definition of Done:**
- E2E tests cover complete flow
- Error scenarios tested
- Performance acceptable (< 500ms per step)

---

### Story 13: CORS Configuration and Frontend Integration

**Story ID:** PROD-113  
**Story Points:** 2  
**Priority:** High  
**Dependencies:** LIMS2-101, PROD-110

**Description:**

As a **Backend Engineer**, I want to configure CORS to allow frontend access, so that the UI can communicate with the backend API without browser security errors.

**Technical Details:**
- Create `CorsConfig` class
- Allow origins: localhost:5173, localhost:3000, localhost:4200
- Allow all HTTP methods and headers
- Enable credentials support

**Acceptance Criteria:**

✅ CorsFilter bean configured  
✅ Allows origins: http://localhost:5173, http://localhost:3000, http://localhost:4200  
✅ Allows methods: GET, POST, PUT, DELETE, PATCH, OPTIONS  
✅ Allows all headers  
✅ Credentials enabled (allowCredentials: true)  
✅ Preflight requests cached for 1 hour  
✅ No CORS errors in browser console  
✅ OPTIONS preflight requests handled correctly  

**Definition of Done:**
- CORS tested from multiple frontend ports
- Preflight requests work correctly
- Production CORS config documented

---

### Story 14: Jakarta Bean Validation and Error Handling

**Story ID:** PROD-114  
**Story Points:** 3  
**Priority:** Medium  
**Dependencies:** LIMS2-101

**Description:**

As a **Backend Engineer**, I want to add automatic request validation with structured error responses, so that invalid requests are caught early with clear error messages.

**Technical Details:**
- Add `spring-boot-starter-validation` dependency
- Annotate models with @NotBlank, @NotNull
- Create `GlobalExceptionHandler` for validation errors
- Return structured error responses

**Acceptance Criteria:**

✅ `@NotBlank` on OperationalRequest.query  
✅ `@NotBlank` on StepExecutionRequest.taskId  
✅ `@NotNull` on StepExecutionRequest.stepNumber  
✅ `@Valid` annotation on all controller @RequestBody parameters  
✅ GlobalExceptionHandler catches MethodArgumentNotValidException  
✅ Returns 400 Bad Request with structured error:  
  - timestamp  
  - status: 400  
  - error: "Validation Failed"  
  - errors: {field: message}  
✅ Generic exception handler for unexpected errors  
✅ All error responses include timestamp  

**Definition of Done:**
- Validation tested with missing/null fields
- Error responses documented
- Frontend handles validation errors gracefully

---

### Story 15: Comprehensive Technical Documentation

**Story ID:** PROD-115  
**Story Points:** 5  
**Priority:** Medium  
**Dependencies:** All other stories

**Description:**

As a **Technical Writer**, I want to create comprehensive documentation covering architecture, deployment, and usage, so that engineers and stakeholders understand the system.

**Technical Details:**
- Technical Requirements Document (TRD)
- Deployment Guide for AWS ECS
- Security Setup Guide
- UI Integration Guide (framework-agnostic)
- API Examples
- Pattern Matching Examples

**Acceptance Criteria:**

✅ TRD.md created with:  
  - Problem statement  
  - Solution overview  
  - Tech stack  
  - Architecture diagrams  
  - API specifications  
  - Class diagram  
  - Security considerations  
✅ DEPLOYMENT_GUIDE.md with AWS ECS step-by-step instructions  
✅ SECURITY_SETUP.md with JWT configuration and architecture  
✅ UI_INTEGRATION_GUIDE.md (framework-agnostic)  
✅ UI_FALLBACK_FLOW.md with manual task selection flow  
✅ PATTERN_MATCHING_EXAMPLES.md with test cases  
✅ README.md with quick start and overview  
✅ All API endpoints documented with examples  

**Definition of Done:**
- Documentation reviewed by tech lead
- All diagrams render correctly
- Examples tested and verified
- Links working

---

## Epic Summary

### Total Story Points: 49

### Development Timeline (Estimated)

**Sprint 1 (2 weeks):**
- LIMS2-101: Repository Setup (2 points)
- LIMS2-103: Classification and Entity Extraction (8 points)
- PROD-113: CORS Configuration (2 points)
- PROD-114: Validation and Error Handling (3 points)
- **Sprint Total:** 15 points

**Sprint 2 (2 weeks):**
- LIMS2-104: Cancel Case Runbook (5 points)
- LIMS2-105: Update Case Status Runbook (5 points)
- LIMS2-107: Step Execution Service (8 points)
- **Sprint Total:** 18 points

**Sprint 3 (2 weeks):**
- LIMS2-102: Infrastructure Provisioning (5 points)
- LIMS2-108: JWT Authentication (5 points)
- LIMS2-109: Swagger Documentation (3 points)
- LIMS2-106: Fallback API (3 points)
- **Sprint Total:** 16 points

**Sprint 4 (2 weeks):**
- PROD-110: Frontend Query Input (5 points)
- PROD-111: Frontend Task Selector (3 points)
- PROD-112: Frontend Step Execution (8 points)
- **Sprint Total:** 16 points

**Sprint 5 (1 week):**
- PROD-115: Documentation (5 points)
- Testing and polish
- **Sprint Total:** 5 points

**Total Duration:** ~9 weeks (5 sprints)

---

## Risk Assessment

### High Risks
- **Downstream API availability** - Mitigation: Use mock API for development
- **JWT token format changes** - Mitigation: Make decoder configurable
- **Pattern detection accuracy** - Mitigation: Fallback to manual selection

### Medium Risks
- **Performance at scale** - Mitigation: Load testing, caching strategy
- **Runbook maintenance** - Mitigation: Version control, review process

### Low Risks
- **Frontend framework choice** - Mitigation: Framework-agnostic API design
- **ECS deployment complexity** - Mitigation: Detailed deployment guide

---

## Success Metrics

### Development Metrics
- ✅ All 15 stories completed
- ✅ 90%+ code coverage
- ✅ Zero critical security vulnerabilities
- ✅ All API endpoints < 500ms p99 latency

### Business Metrics (Post-Launch)
- 70% reduction in operational task time
- 90% reduction in operational errors
- 80% engineer adoption within 3 months
- 10+ operational patterns supported

---

## Dependencies Matrix

| Story | Depends On |
|-------|------------|
| LIMS2-101 | None |
| LIMS2-102 | LIMS2-101 |
| LIMS2-103 | LIMS2-101 |
| LIMS2-104 | LIMS2-103 |
| LIMS2-105 | LIMS2-103 |
| LIMS2-106 | LIMS2-103, LIMS2-104, LIMS2-105 |
| LIMS2-107 | LIMS2-104, LIMS2-105 |
| LIMS2-108 | LIMS2-101 |
| LIMS2-109 | LIMS2-103, LIMS2-104, LIMS2-105, LIMS2-107 |
| PROD-110 | LIMS2-103, LIMS2-109 |
| PROD-111 | LIMS2-106, PROD-110 |
| PROD-112 | LIMS2-107, PROD-110 |
| PROD-113 | LIMS2-101, PROD-110 |
| PROD-114 | LIMS2-101 |
| PROD-115 | All stories |

---

**Document Owner:** Engineering Team  
**Last Updated:** November 6, 2025  
**Status:** Ready for Sprint Planning

