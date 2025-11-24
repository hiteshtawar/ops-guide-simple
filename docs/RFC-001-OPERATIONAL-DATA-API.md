# RFC-001: Operational Data API for Production Support Autonomy

**Status:** Proposed  
**Author:** Engineering Team  
**Created:** 2025-11-17  
**Last Updated:** 2025-11-17  

---

## Executive Summary

Introduce a new module - operational data service (`<lims-histology-data-api>`) to enable autonomous deployment of production support operations without dependency on core service (`ap-services`) release cycles. This pattern trades controlled logic duplication from ap-services for operational agility and reduced downtime risk for LIMS Histology.

**Key Benefits:**
- ⚡ Ship new operations in minutes, not weeks
- 🔒 Zero risk to core service stability
- 🚀 Independent deployment and scaling
- 🎯 Production Support team autonomy and independence from release planning

**Trade-offs:**
- Some business logic duplication
- Two services to maintain
- Eventual consistency considerations

---

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Current Architecture](#current-architecture)
3. [Proposed Architecture](#proposed-architecture)
4. [Detailed Design](#detailed-design)
5. [Database Connection Management](#database-connection-management)
6. [Trade-offs Analysis](#trade-offs-analysis)
7. [Implementation Plan](#implementation-plan)
8. [Open Questions](#open-questions)
9. [Decision](#decision)

**For Engineers:** See [RFC-001 Implementation Guide](./RFC-001-IMPLEMENTATION-GUIDE.md) for code examples, configurations, and deployment details.

---

## Problem Statement

### Current Challenges

**Problem 1: Deployment Dependency**
```
Need new operation → Update ap-services → Release → QA → Deploy → Prod
Timeline: 2-4 weeks per operation
```

**Problem 2: Release Coordination**
- ap-services has ~10 teams contributing
- Release windows: Bi-weekly (with planning required)
- Small operational changes bundled with feature releases
- Rollback affects entire service

**Problem 3: Risk Profile**
- Operational fixes touch production-critical code
- Testing burden on ap-services team
- One bug in operational code = entire service at risk

**Problem 4: Team Autonomy**
- Production Support team blocked by ap-services roadmap
- Cannot respond quickly to operational needs
- Dependency on other team's release schedule

### Example: Cancel Case Operation

**Current Flow:**
```
User: "Need to cancel cases via UI"
└─> Production Support: "Add cancel endpoint to ap-services"
    └─> ap-services Team: "Added to sprint 3, release in 3 weeks"
        └─> Testing & QA: 1 week
            └─> Production Deploy: Wait for next release window
                └─> Total: 4 weeks minimum
```

---

## Current Architecture

### As-Is Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        Frontend UI                            │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                     API Gateway                               │
│              (Auth, Rate Limiting, Routing)                   │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────┐
│              production-support-admin                         │
│  ┌────────────────────────────────────────────────────┐      │
│  │ - YAML Runbook Parser                              │      │
│  │ - Pattern Classification                           │      │
│  │ - Entity Extraction                                │      │
│  │ - Step Orchestration                               │      │
│  └────────────────────────────────────────────────────┘      │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            │ ALL operations
                            │ go through here
                            ▼
┌──────────────────────────────────────────────────────────────┐
│                      ap-services                              │
│  ┌────────────────────────────────────────────────────┐      │
│  │ - Core Business Logic                              │      │
│  │ - Domain Models                                    │      │
│  │ - Workflows                                        │      │
│  │ - Data Access Layer                                │      │
│  │ - Operational Endpoints (mixed with core)          │      │
│  └────────────────────────────────────────────────────┘      │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
                    ┌───────────────┐
                    │   Database    │
                    └───────────────┘
```

### Current Operation Flow: Cancel Case

```
1. User Request
   POST /api/v1/execute-step
   { taskId: "CANCEL_CASE", stepNumber: 3, caseId: "2025123P6732" }
   
2. production-support-admin
   - Parses YAML runbook
   - Resolves placeholders
   - Builds HTTP request
   
3. Calls ap-services
   PATCH /lims-api/case/2025123P6732/cancel
   { reason: "operational_request", notify_stakeholders: true }
   
4. ap-services
   - Validates case exists
   - Updates case.status = "cancelled"
   - Triggers workflow cleanup
   - Sends notifications
   - Returns 200 OK

5. production-support-admin
   - Runs postchecks
   - Returns result to UI
```

**Problem:**
- To add this endpoint → ap-services code change → release cycle
- To modify cancellation logic → ap-services code change → release cycle
- To fix bugs → ap-services code change → release cycle

---

## Proposed Architecture

### To-Be Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        Frontend UI                            │
└────────────┬──────────────────────────────┬──────────────────┘
             │                              │
             │ Production Support           │ Business Features
             │ Operations                   │ (Case mgmt, workflows)
             ▼                              ▼
┌──────────────────────────────────────────────────────────────┐
│                     API Gateway                               │
│         (Auth, Rate Limiting, Routing)                        │
└────────────┬─────────────────────────────┬───────────────────┘
             │                             │
             │ /production-support-admin   │ /ap-services
             ▼                             ▼
┌──────────────────────────────┐  ┌──────────────────────────────┐
│ production-support-admin     │  │       ap-services            │
│  ┌────────────────────────┐  │  │  ┌──────────────────────┐   │
│  │ - YAML Runbook Parser  │  │  │  │ Core Business Logic  │   │
│  │ - Classification       │  │  │  │ - Case Management    │   │
│  │ - Entity Extraction    │  │  │  │ - Workflows          │   │
│  │ - Step Orchestration   │  │  │  │ - Sample Processing  │   │
│  │ - Smart Routing (NEW)  │  │  │  │ - Reporting          │   │
│  └────────────┬───────────┘  │  │  │ - Analytics          │   │
└───────────────┼──────────────┘  │  └──────────────┬───────┘   │
                │                 │                 │           │
                │ Routes ALL      │                 │           │
                │ operations to:  │                 │           │
                ▼                 │                 ▼           │
┌─────────────────────────────┐  │  ┌─────────────────────────┐ │
│  <lims-histology-data-api>  │  │  │     Database            │ │
│  ┌────────────────────────┐ │  │  │ (Shared LIMS DB)        │ │
│  │ Operational Endpoints: │ │  │  │                         │ │
│  │                        │ │  │  │ Tables:                 │ │
│  │ - Cancel Case          │◄─┼──┼──│ - cases                │◄┤
│  │ - Update Status        │ │  │  │ - case_audit_log       │ │
│  │ - Bulk Updates         │ │  │  │ - samples              │ │
│  │ - Data Corrections     │ │  │  │ - materials            │ │
│  │ - Status Verification  │ │  │  │ - workflows            │ │
│  │                        │ │  │  └─────────────────────────┘ │
│  │ Includes:              │ │  │                              │
│  │ - Writes (updates)     │ │  │  ap-services uses DB for     │
│  │ - Reads (postchecks)   │ │  │  core business features      │
│  │ - Audit logging        │ │  └──────────────────────────────┘
│  │ - Validation           │ │
│  └────────────┬───────────┘ │
│               │             │
└───────────────┼─────────────┘
                │
                │ All DB operations
                ▼
            ┌────────────────────────────────────────┐
            │            Database                     │
            │  (Shared - Single Source of Truth)     │
            │                                         │
            │  Connection Pool Management:            │
            │  - <lims-histology-data-api>: 10 conns │
            │  - ap-services: 40 conns               │
            │  - Other services: 35 conns            │
            │  - Buffer: 15 conns                    │
            │  Total: 100 max connections            │
            └────────────────────────────────────────┘
```

### Key Architectural Decisions

#### 1. Service Responsibility Split

| Concern | production-support-admin | <lims-histology-data-api> | ap-services |
|---------|-------------------------|---------------------------|-------------|
| **Runbook orchestration** | ✓ Owns | - | - |
| **Pattern matching** | ✓ Owns | - | - |
| **Operational writes** | Routes | ✓ Owns | - |
| **Operational reads** | Routes | ✓ Owns | - |
| **Core business logic** | - | - | ✓ Owns |
| **Business features** | - | - | ✓ Serves |
| **Database access** | - | ✓ Direct | ✓ Direct |

#### 2. Routing Strategy

**YAML Runbook Configuration:**

```yaml
# cancel-case.yaml
useCase:
  id: "CANCEL_CASE"
  name: "Cancel Case"
  downstreamService: "lims-histology-data-api"  # <-- ALL operations route to new service
  
execution:
  steps:
    - stepNumber: 3
      stepType: "procedure"
      method: "PATCH"
      path: "/operations/case/{case_id}/cancel"  # <-- Write operation
      
    - stepNumber: 4
      stepType: "postchecks"
      method: "GET"
      path: "/operations/case/{case_id}/status"  # <-- Read from same service
      # No override needed - all operations self-contained
```

**Benefits:**
- ✅ No code changes to production-support-admin
- ✅ Just change YAML configuration
- ✅ Self-contained service (no cross-service dependencies for operations)
- ✅ Faster: All operations hit same service (no service-hopping)

---

## Detailed Design

### Component Interaction

```
┌─────────────────────────────────────────────────────────────────┐
│                     Request Flow: Cancel Case                    │
└─────────────────────────────────────────────────────────────────┘

1. User Request
   │
   ▼
┌──────────────────────────┐
│ production-support-admin │
│ POST /execute-step       │
└──────────┬───────────────┘
           │
           │ Step 1: Permission Check (HEADER_CHECK - local)
           │ ✓ User has "Production Support" role
           │
           │ Step 2: Preview Impact (LOCAL_MESSAGE - local)
           │ ✓ "Case will be cancelled..."
           │
           │ Step 3: Execute Cancellation
           ├──────────────────────────────────────────┐
           │                                          │
           ▼                                          │
┌─────────────────────────────────┐                  │
│ <lims-histology-data-api>       │                  │
│ PATCH /operations/case/         │                  │
│       2025123P6732/cancel       │                  │
│                                 │                  │
│ 1. Validate case exists         │◄─────────────────┘
│ 2. Check cancellation allowed   │
│ 3. UPDATE cases                 │
│    SET status = 'cancelled'     │
│ 4. INSERT into case_audit_log   │
│ 5. Trigger workflow cleanup     │
│ 6. Send notifications           │
│ 7. Return 200 OK                │
└─────────────────────────────────┘
           │
           │ Response: { "status": "cancelled", "timestamp": "..." }
           │
           ▼
┌──────────────────────────┐
│ production-support-admin │
└──────────┬───────────────┘
           │
           │ Step 4: Verify Status (postcheck)
           ├──────────────────────────────────────────┐
           │                                          │
           ▼                                          │
┌─────────────────────────────────┐                  │
│ <lims-histology-data-api>       │                  │
│ GET /operations/case/           │◄─────────────────┘
│     2025123P6732/status         │
│                                 │
│ 1. SELECT status FROM cases     │
│    WHERE id = '2025123P6732'    │
│ 2. Return status                │
└─────────────────────────────────┘
           │
           │ Response: { "status": "cancelled" }
           │
           ▼
┌──────────────────────────┐
│ production-support-admin │
│ All steps complete ✓     │
└──────────────────────────┘
```

### Database Access Pattern

Both services access the same database but with clear API contracts for CRUD operations:


**<lims-histology-data-api> Responsibilities:**
- ✅ Operational writes (cancel, update, bulk operations)
- ✅ Operational reads (status verification, audit logs)
- ✅ Self-contained: No dependency on ap-services for operations

**ap-services Responsibilities:**
- ✅ Core business operations
- ✅ Complex workflows
- ✅ Feature development
- ❌ No longer called for operational support tasks

---

## Database Connection Management

### Challenge: Shared Database, Multiple Services

**The Problem:** Multiple services competing for limited database connections (typically 100 max). Adding a new service requires careful connection allocation to prevent impacting existing services.

### Connection Pool Strategy

**Key Principles:**

1. **Conservative Allocation:** `<lims-histology-data-api>` gets 10 connections (10% of total)
   - Low volume service (10-100 operations/day)
   - Prevents resource starvation

2. **Database-Level Protection:**
   - Dedicated database user with hard connection limit (12 max)
   - Statement timeout (10s) prevents runaway queries
   - Least privilege permissions (SELECT, INSERT, UPDATE on specific tables)

3. **Connection Pool Distribution:**
   - ap-services: 75 connections (primary service)
   - <lims-histology-data-api>: 10 connections (operational service)
   - Buffer: 15 connections (admin, migrations, emergency)

**Configuration Details:** See [Implementation Guide - Database Setup](./RFC-001-IMPLEMENTATION-GUIDE.md#database-connection-setup)

### Protection Mechanisms Summary

1. **HikariCP Connection Pool:** Configured with 10 max connections, leak detection, fast-fail timeouts
2. **Transaction Timeouts:** 10s max to prevent hung transactions
3. **Circuit Breaker:** Fails fast if database struggling
4. **Monitoring:** Track pool utilization, alert at 80% threshold
5. **Health Checks:** Expose connection pool metrics

**Detailed Configuration:** See [Implementation Guide](./RFC-001-IMPLEMENTATION-GUIDE.md) for code examples, SQL scripts, monitoring dashboards, and incident response playbooks.

---

## Example: Cancel Case Operation Flow

**YAML Runbook (production-support-admin):**
```yaml
useCase:
  id: "CANCEL_CASE"
  downstreamService: "lims-histology-data-api"  # Self-contained service

execution:
  steps:
    - stepNumber: 3
      method: "PATCH"
      path: "/operations/case/{case_id}/cancel"  # Write operation
      
    - stepNumber: 4
      method: "GET"
      path: "/operations/case/{case_id}/status"  # Read (same service)
```

**Deployment Impact:**
- **Before:** Change ap-services → Release cycle → 2-4 weeks
- **After:** Change `<lims-histology-data-api>` → Deploy → 1-3 days

**API Implementation:** See [Implementation Guide - API Examples](./RFC-001-IMPLEMENTATION-GUIDE.md#api-implementation-examples)

---

## Trade-offs Analysis

### Benefits

**1. Deployment Independence**
- Before: 2-4 weeks (ap-services release cycle)
- After: 1-3 days (independent deployment)

**2. Risk Isolation**
- Bug in operational code → Only operational service affected
- Core service (ap-services) remains stable

**3. Team Autonomy**
- Production Support team controls their roadmap
- No dependency on ap-services sprint planning

**4. Independent Scaling**
- Operational service: Low volume, can scale conservatively
- Core service: High volume, scales independently

### Costs

**1. Logic Duplication**
- Validation rules, business rules duplicated
- Mitigation: Shared library for core domain models

**2. Maintenance Overhead**
- Two services to deploy, monitor, maintain
- Additional CI/CD pipeline

**3. Database Connection Management**
- Must carefully allocate connections (10 for ops service)
- Database-level limits prevent impact to other services

**4. Potential Data Inconsistency**
- Both services write to same database
- Mitigation: Database constraints, optimistic locking, audit logging

### Risk Matrix

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|---------|------------|
| **Logic drift** | High | Medium | Shared library + governance |
| **Race conditions** | Low | High | Optimistic locking + transactions |
| **Database contention** | Low | Low | Connection limits per service |
| **Deployment complexity** | Low | Low | Infrastructure as code |

---

## Implementation Plan

### Phase 1: MVP - Cancel Case (Week 1-4)

**Setup:**
1. Create `<lims-histology-data-api>` Spring Boot service
2. Configure database access (10 connection limit)
3. Set up CI/CD pipeline
4. Add service to production-support-admin WebClientRegistry

**Implementation:**
1. Implement cancel case endpoint
2. Implement status verification
3. Update YAML runbook routing
4. Add monitoring and alerts

**Validation:**
- Dark launch (1% traffic) → Full rollout
- Zero ap-services deployments required

**Success Criteria:** Deploy new cancellation feature in < 1 day

---

### Phase 2: Scale Operations (Week 5-8)

**Migrate Operations:**
- Update Sample Status
- Bulk Updates
- Data Corrections

**Establish Patterns:**
- Shared validation library
- Contract testing
- Deployment automation

---

## Open Questions

### 1. Service Naming

**Question:** What should we name `<lims-histology-data-api>`?

**Options:**
- `lims-operations-api` (generic, future-proof)
- `lims-histology-operations-api` (specific, clear scope)
- `lims-support-data-api` (support-focused)
- `lims-admin-operations-api` (admin-focused)

**Recommendation:** `lims-operations-api` - Generic enough for growth

---

### 2. Rollback Strategy

**Question:** What if we need to rollback to ap-services?

**Strategy:**
1. Keep ap-services endpoints for 6 months
2. YAML runbook controls routing
3. Emergency rollback:
   - Change YAML: `downstreamService = "ap-services"`
   - Deploy production-support-admin
   - No code changes needed

**Recommendation:** Build rollback capability from day 1

---

## Decision

### Recommendation: ✅ PROCEED with Proposed Architecture

**Rationale:**
1. **Autonomy Value > Duplication Cost** - Operational agility worth the maintenance overhead
2. **Risk Mitigation** - Shared library reduces drift, rollback strategy provides safety net
3. **Proven Pattern** - Similar to BFF pattern used by Netflix, Uber, Amazon
4. **Reversible Decision** - Can consolidate back if needed via YAML routing

### Approval Criteria

**Must Have:**
- [ ] Approval from ap-services team lead
- [ ] Approval from Production Support team lead
- [ ] Database team review of connection allocation

**Success Metrics (3 months):**
- [ ] 100% of operational features deployed without ap-services release
- [ ] < 2 days average time to deploy new operation
- [ ] Zero incidents affecting core service
- [ ] Connection pool utilization < 80%

---

## Next Steps

1. **Week 1:** Socialize RFC, gather feedback, finalize service name
2. **Week 2:** Kick off implementation
3. **Week 4:** Pilot with new service
4. **Week 8:** Retrospective

---

## References

- [RFC-001 Implementation Guide](./RFC-001-IMPLEMENTATION-GUIDE.md) - Code examples, configurations
- [Production Support Runbook Guidelines](./RUNBOOK_GUIDELINES.md) - YAML structure
- [System Architecture](./ARCHITECTURE.md) - Overall architecture

---

**Discussion Points:**

1. What's the right name for `<lims-histology-data-api>`?
2. Approval from ap-services, database, and infra teams
3. Database connection allocation (10 connections sufficient?)

**Please provide feedback by:** 2025-11-24

