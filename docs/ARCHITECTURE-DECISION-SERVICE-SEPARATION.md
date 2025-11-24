# Architecture Decision: Service Separation for lims-operations-api

## Context

**Question:** Should `lims-operations-api` be embedded in `production-support-admin` or remain a separate service?

**Current Design:** `production-support-admin` is a **YAML-driven operational automation platform** that enables **zero-code deployment** of new use cases with **infinite horizontal scaling**.

## Recommendation: Keep Services Separate ✅

## Architecture Comparison

### Proposed Architecture (Decoupled)
```
┌─────────────────────────────────┐
│  production-support-admin       │
│  (Service Agnostic Platform)    │
│  - Runbook orchestration        │
│  - Pattern matching             │
│  - Classification               │
│  - Routing only                 │
└──────────┬──────────────────────┘
           │ Routes to...
           ├────────────────┬─────────────────┐
           ▼                ▼                 ▼
    ┌──────────────┐  ┌─────────────┐  ┌──────────┐
    │ lims-        │  │ datadog-    │  │ splunk-  │
    │ operations-  │  │ api         │  │ api      │
    │ api          │  │             │  │          │
    └──────────────┘  └─────────────┘  └──────────┘
```

### Alternative (Tight Coupling)
```
┌──────────────────────────────────────┐
│  production-support-admin            │
│  - Runbook orchestration             │
│  - LIMS operational endpoints        │
│  - Histology data operations         │
│  - Database access to LIMS DB        │
└──────────────────────────────────────┘
     ❌ Now tightly coupled to LIMS
```

## Key Arguments for Separation

### 1. Separation of Concerns

**Platform ≠ Feature Service**

- `production-support-admin` is an **orchestration platform**, not a data service
- Mixing orchestration with operational data violates Single Responsibility Principle
- Platform should route requests, not own data operations

**Per RFC-001-OPERATIONAL-DATA-API.md:**
```
| Concern              | production-support-admin | lims-operations-api |
|----------------------|--------------------------|---------------------|
| Operational reads    | Routes                   | ✓ Owns              |
| Operational writes   | Routes                   | ✓ Owns              |
| Database access      | -                        | ✓ Direct            |
```

### 2. Tight Coupling Problems

If `lims-operations-api` is embedded in `production-support-admin`:

❌ **Schema coupling:** LIMS schema changes require production-support-admin redeployment  
❌ **Logic pollution:** LIMS-specific business logic pollutes the platform layer  
❌ **Database dependencies:** Platform now manages LIMS database connections  
❌ **Testing complexity:** Cannot test LIMS operations independently  
❌ **Deployment coupling:** Cannot deploy LIMS changes without touching orchestration  

### 3. Horizontal Scaling Independence

**Decoupled Approach:**
- `production-support-admin` scales based on **orchestration load** (request classification, routing)
- `lims-operations-api` scales based on **data operation load** (queries, updates)
- Different load patterns, different scaling needs

**Coupled Approach:**
- Single service must scale for both patterns
- Inefficient resource allocation
- Higher operational complexity

### 4. Service Agnostic = Future Proof

**With Separation:**
```java
production-support-admin
  ├── Routes to lims-operations-api
  ├── Routes to datadog-api                   // Future
  ├── Routes to splunk-api                    // Future
  └── Routes to any-new-downstream-service    // Future
```

**Platform Characteristics:**
- ✅ Service agnostic orchestration
- ✅ Zero-code deployment for new use cases
- ✅ Infinite horizontal scaling
- ✅ Easy to plug in new downstream services

**With Embedding:**
```java
production-support-admin
  ├── Contains LIMS operations (tightly coupled)
  ├── Contains Datadog integration? (growing monolith)
  ├── Contains Splunk integration? (bloated)
  └── Contains everything? (defeats the platform vision)
```

**Anti-patterns:**
- ❌ Growing monolith
- ❌ Code changes required for new features (breaks zero-code promise)
- ❌ Difficult to extend without bloating the service

### 5. The Zero-Code Deployment Promise

**Claim:** "YAML-driven operational automation platform with zero-code deployment"

**With Separation:**
- ✅ Add new LIMS operations in `lims-operations-api` without touching platform
- ✅ Add new YAML runbooks in `production-support-admin` without touching operational services
- ✅ True zero-code deployment for new use cases

**With Embedding:**
- ❌ Every new LIMS feature requires code changes in orchestration service
- ❌ Breaks the zero-code deployment promise
- ❌ Platform becomes a feature service, not an orchestration layer

### 6. Database Connection Management

**Separation:**
- `lims-operations-api` owns connection pools to LIMS database
- `production-support-admin` has no database dependencies
- Clean separation of concerns

**Embedding:**
- `production-support-admin` needs connection pools to LIMS DB
- Couples orchestration layer to specific database schemas
- Complex connection management and transaction handling
- Security: orchestration service now has direct DB access

### 7. Testing & Deployment Independence

**Decoupled Benefits:**
- Test LIMS operations independently with focused test suites
- Deploy LIMS changes without affecting orchestration
- Rollback LIMS changes without platform impact
- Independent CI/CD pipelines
- Faster feedback loops

**Coupled Drawbacks:**
- Integration tests become more complex
- Deployment requires full regression testing
- Rollback affects entire platform
- Slower release cycles

### 8. Team Autonomy

**With Separation:**
- Platform team owns orchestration logic
- LIMS team owns data operations
- Clear ownership boundaries
- Parallel development without conflicts

**With Embedding:**
- Both teams work in same codebase
- Increased merge conflicts
- Coordination overhead
- Slower velocity

## Technical Concerns Addressed

### "Too Many Services?"

**Response:** 
- Microservices architecture is about logical boundaries, not service count
- Each service has clear responsibility
- Operational overhead is manageable with modern tooling (K8s, service mesh)
- The alternative (monolith) has worse long-term costs

### "Network Latency?"

**Response:**
- Latency from one extra hop is negligible (< 1ms in same cluster)
- Benefit of independent scaling outweighs minimal latency
- Can implement caching strategies if needed
- gRPC or service mesh can optimize inter-service communication

### "Operational Complexity?"

**Response:**
- Modern platforms (Kubernetes, Istio) handle service orchestration well
- Observability tools (Datadog, Splunk) work better with separated services
- Easier to debug isolated failures
- Better separation makes incidents easier to resolve

## Decision

**Recommendation:** Keep `lims-operations-api` as a separate service

**Rationale:**
1. Maintains platform's service-agnostic design
2. Enables true zero-code deployment promise
3. Prevents tight coupling and monolith anti-pattern
4. Supports infinite horizontal scaling vision
5. Follows documented architecture (RFC-001)
6. Enables future extensibility (monitoring integrations, alerting)
7. Maintains clear separation of concerns

## Implementation Path

### Phase 1: Current State (Recommended)
```
production-support-admin (orchestration)
    ↓ routes to
lims-operations-api (LIMS operations)
```

### Phase 2: Future Growth
```
production-support-admin (orchestration)
    ├── lims-operations-api
    ├── datadog-api
    └── splunk-api
```

## Conclusion

**The platform promise is:**
> "YAML-driven operational automation platform that enables zero-code deployment of new use cases with infinite horizontal scaling"

**Embedding LIMS operations breaks this promise by:**
- Coupling orchestration to specific downstream logic
- Requiring code changes for new features
- Creating a growing monolith
- Limiting horizontal scaling independence

**Separation maintains the promise by:**
- Keeping the platform service-agnostic
- Enabling true zero-code YAML-driven deployment
- Supporting infinite horizontal scaling
- Making it easy for any service to hook into the platform

---

**Decision:** Maintain service separation. Let `production-support-admin` be the platform it was designed to be.

