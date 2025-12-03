# Production Support Platform: Architecture Evolution

**A Strategic 3-Phase Journey from Manual Operations to ML-Powered Incident Orchestration**

---

## Phase 1: Current Architecture - Tightly Coupled Operations (Today)

### Overview
All operational requests flow through production-support-admin to ap-services. Operational endpoints are embedded within the core business service, requiring code changes and ap-services deployments for new operations.

### Architecture

```
┌─────────────────────┐
│   Frontend UI       │
│   (Operator)        │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   API Gateway       │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────────────────┐
│  production-support-admin       │
│  • YAML Runbook Parser          │
│  • Pattern Classification       │
│  • Entity Extraction            │
│  • Step Orchestration           │
└──────────┬──────────────────────┘
           │
           │ ALL operations
           ▼
┌─────────────────────────────────┐
│      ap-services                │
│  • Core Business Logic          │
│  • Operational Endpoints        │
│  • (Mixed Together)             │
└──────────┬──────────────────────┘
           │
           ▼
    ┌──────────┐
    │ Database │
    └──────────┘
```

### Characteristics
- **Operational Flow:** Manual operator queries → Classification → Direct ap-services calls
- **Coupling:** Operational endpoints embedded in core business service
- **Deployment:** New operations require ap-services code changes
- **Scaling:** Single service must handle both business and operational load
- **Team Impact:** Production support team dependent on ap-services release cycles

### Pain Points
❌ Code changes required for new operational endpoints  
❌ ap-services release cycle delays operational support  
❌ Tight coupling between operations and core business  
❌ Cannot scale operational and business logic independently  
❌ Difficult to adopt by other teams  

---

## Phase 2: Service Separation - Platform Independence (Near-Term)

### Overview
Extract operational endpoints into dedicated `connect-assist-api`. Platform becomes service-agnostic orchestrator that routes YAML-driven runbooks to appropriate downstream services. connect-assist-api exposes operational endpoints and data patch APIs that incident smart advisor steps can call.

### Architecture

```
┌─────────────────────┐
│   Frontend UI       │
│   (Operator)        │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   API Gateway       │
└──────────┬──────────┘
           │
           ▼
┌──────────────────────────────────────┐
│  production-support-admin            │
│  (Service-Agnostic Orchestrator)     │
│  • YAML Runbook Parser               │
│  • Pattern Classification            │
│  • Entity Extraction                 │
│  • Smart Routing (NEW)               │
└──────────┬───────────────────────────┘
           │
           │ Routes to appropriate service
           │
           ├─────────────────┬─────────────────┐
           ▼                 ▼                 ▼
    ┌─────────────┐   ┌──────────┐   ┌──────────┐
    │  connect-   │   │ datadog- │   │ splunk-  │
    │  assist-    │   │   api    │   │   api    │
    │    api      │   │          │   │          │
    │ (NEW)       │   │ (Future) │   │ (Future) │
    └──────┬──────┘   └──────────┘   └──────────┘
           │
           ▼
    ┌──────────┐          ┌─────────────────┐
    │ Database │          │   ap-services   │
    │          │◄─────────│ Core Business   │
    └──────────┘          │ Logic Only      │
                          └─────────────────┘
```

### Characteristics
- **Operational Flow:** Manual operator queries → Classification → Routed to specific operational API
- **Decoupling:** Operational endpoints separated from core business logic
- **Deployment:** Add YAML runbooks without code changes (zero-code deployment)
- **Scaling:** Each service scales independently based on load patterns
- **Team Impact:** Multi-team adoption enabled; platform remains neutral

### Key Benefits
✅ **Multi-team adoption** - Platform is service-agnostic  
✅ **Independent scaling** - Operations and business scale separately  
✅ **Zero-code deployment** - Add new use cases via YAML  
✅ **Incident resilience** - Orchestration layer stays responsive  
✅ **Faster operations** - No ap-services release cycle dependency  

### Success Metrics
- **Current state:** 1 operational service (connect-assist-api), ~20 operations, low volume
- **Near-term goal:** Onboard 2nd service (datadog-api or splunk-api) to validate pattern
- **Optimizing for:** Multi-team adoption + incident response resilience

### What is connect-assist-api?
Dedicated service that exposes:
- **Operational endpoints:** Cancel case, update status, bulk updates, data corrections
- **Data patch APIs:** Quick fixes and status verification for incidents
- **Smart advisor integration:** APIs that incident smart advisor steps can call for remediation

---

## Phase 3: ML-Powered Incident Orchestration (Future Vision)

### Overview
Enhance orchestration platform with ML Suggestion Engine to provide intelligent, context-aware incident remediation recommendations. Platform learns from historical incidents and suggests corrective actions with confidence scoring.

### Architecture

```
┌─────────────────────┐
│   Frontend UI       │
│ (Natural Language   │
│  Operator Query)    │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   API Gateway       │
└──────────┬──────────┘
           │
           ▼
┌──────────────────────────────────────────────┐
│  production-support-admin                    │
│  (ML-Enhanced Incident Orchestrator)         │
│                                              │
│  ┌────────────────────────────────────────┐ │
│  │  Runbook Engine                        │ │
│  │  • YAML-driven workflows               │ │
│  │  • Pattern matching                    │ │
│  │  • Entity extraction                   │ │
│  └────────────────┬───────────────────────┘ │
│                   │                          │
│                   │ Incident Context         │
│                   ▼                          │
│  ┌────────────────────────────────────────┐ │
│  │  ML Suggestion Engine (NEW)            │ │
│  │  • Rule + Model inference              │ │
│  │  • Confidence scoring                  │ │
│  │  • Historical pattern learning         │ │
│  └────────────────┬───────────────────────┘ │
└───────────────────┼──────────────────────────┘
                    │
                    │ ML Recommendations
                    ▼
         ┌──────────────────────┐
         │   AWS (Minimal)      │
         │  ┌────────────────┐  │
         │  │ S3             │  │
         │  │ • Incident     │  │
         │  │   history      │  │
         │  │ • Model        │  │
         │  │   artifacts    │  │
         │  └────────┬───────┘  │
         │           │          │
         │           ▼          │
         │  ┌────────────────┐  │
         │  │ Lambda         │  │
         │  │ • Inference    │  │
         │  │   API          │  │
         │  │ • Python       │  │
         │  └────────┬───────┘  │
         │           │          │
         │  ┌────────▼───────┐  │
         │  │ SageMaker      │  │
         │  │ (Optional)     │  │
         │  │ • Model        │  │
         │  │   training     │  │
         │  └────────────────┘  │
         └──────────────────────┘
                    │
                    │ Corrective Actions
                    │
         ┌──────────┼───────────────────┐
         ▼          ▼                   ▼
  ┌─────────┐ ┌─────────┐       ┌─────────┐
  │ connect-│ │datadog- │       │ splunk- │
  │ assist- │ │  api    │  ...  │   api   │
  │   api   │ │         │       │         │
  └─────────┘ └─────────┘       └─────────┘
```

### Characteristics
- **Operational Flow:** Natural language query → ML suggestions → Human approval → Orchestrated execution
- **Intelligence:** ML engine learns from historical incidents, suggests remediation steps
- **Safety:** Suggestions are advisory; destructive actions require human approval
- **AWS Footprint:** Minimal (S3 + Lambda + optional SageMaker)
- **Adaptability:** Learns from outcomes, improves recommendations over time

### Key Capabilities

**1. Incident Context Ingestion**
- Error codes, service/endpoint info, payload hints
- Historical pattern matching
- Cross-service correlation

**2. ML Recommendation Engine**
- Rule-based + model inference hybrid
- Confidence scoring (high/medium/low)
- Suggests: runbook steps, corrective actions, investigation paths

**3. Human-in-the-Loop Safety**
- Advisory suggestions only
- Auto-apply only for high-confidence, safe actions (e.g., status updates)
- Destructive actions require explicit approval

**4. Continuous Learning**
- Feedback loop from execution outcomes
- Model retraining on S3 historical data
- Pattern expansion across services

### ML Capabilities
✅ **Intelligent suggestions** - Context-aware recommendations from historical incidents  
✅ **Confidence scoring** - High/medium/low confidence for each suggestion  
✅ **Multi-service patterns** - Learn patterns across LIMS, Datadog, Splunk  
✅ **Minimal AWS footprint** - S3 + Lambda (< 300ms p95)  
✅ **Circuit breakers** - Platform continues if ML unavailable  
✅ **Guardrails** - Only 'safe_actions' auto-applied; destructive actions need approval  

### Rollout Plan

**Phase 0: Observe Mode**
- Add telemetry + S3 logging
- Wire ML call without auto-apply
- Measure ML API latency (< 300ms p95)

**Phase 1: Safe Auto-Apply**
- Enable auto-apply for high-confidence, safe actions only (e.g., status updates on LIMS)
- Human approval required for everything else

**Phase 2: Expand Patterns**
- Add Datadog, Splunk patterns
- Keep business logic separate
- Validate multi-service recommendations

### Success Metrics
- **ML latency:** < 300ms p95; orchestrator continues if ML unavailable
- **Adoption:** Other teams use platform for incident response
- **Time to resolution:** Reduce incident remediation time by 40%+
- **Pattern accuracy:** 85%+ confidence for auto-applied actions

---

## Comparison: 3 Phases at a Glance

| Aspect | Phase 1: Tightly Coupled | Phase 2: Separated | Phase 3: ML-Enhanced |
|--------|-------------------|-------------------|---------------------|
| **Operator Input** | Manual query | Manual query | Natural language query |
| **Intelligence** | Pattern matching | Pattern matching + routing | ML suggestions + orchestration |
| **Service Coupling** | Tight (ap-services) | Loose (separated APIs) | Loose + multi-service |
| **Deployment** | Code changes required | YAML-only (zero-code) | YAML-only + ML updates |
| **Scaling** | Single service | Independent per service | Independent + ML layer |
| **Incident Response** | Manual remediation | Orchestrated workflows | ML-suggested + orchestrated |
| **Multi-team Adoption** | ❌ Difficult | ✅ Enabled | ✅ Enhanced with ML |
| **AWS Footprint** | - | - | Minimal (S3 + Lambda) |
| **Human Safety** | Manual approval | Manual approval | Advisory ML + approval gates |

---

## Migration Path & Timeline

### Phase 1 → Phase 2 (Immediate Priority)
**Duration:** 4-6 weeks

**Steps:**
1. Create `connect-assist-api` service
2. Migrate 20 operational endpoints from ap-services
3. Add data patch APIs for incident remediation
4. Update production-support-admin routing logic
5. Deploy and validate with existing runbooks
6. Onboard 2nd service (datadog-api or splunk-api) to prove pattern

**Risk:** Low - YAML runbooks remain unchanged; only routing changes

### Phase 2 → Phase 3 (Future Enhancement)
**Duration:** 3-4 months (phased rollout)

**Steps:**
1. **Phase 0 (Observe):** Add S3 logging + ML call (no auto-apply) - 4 weeks
2. **Phase 1 (Safe Auto-Apply):** Enable high-confidence, safe actions only - 6 weeks
3. **Phase 2 (Expand):** Add Datadog/Splunk patterns, multi-service learning - 8 weeks

**Risk:** Medium - ML adds complexity; mitigated by circuit breakers and observe mode

---

## Strategic Benefits

### Immediate (Phase 2)
✅ **Multi-team adoption enabled** - Platform becomes service-agnostic  
✅ **Operational velocity** - No ap-services release cycle dependency  
✅ **Independent scaling** - Services scale based on their load patterns  
✅ **Incident resilience** - Orchestration stays responsive during spikes  

### Long-term (Phase 3)
✅ **Intelligent incident response** - ML learns from historical patterns  
✅ **Faster remediation** - Context-aware suggestions reduce investigation time  
✅ **Cross-service patterns** - Learn from LIMS, Datadog, Splunk incidents  
✅ **Continuous improvement** - Feedback loop improves recommendations  
✅ **Platform differentiation** - ML-powered orchestration is competitive advantage  

---

## Recommendation

**Proceed with Phase 2 immediately** to unlock multi-team adoption and operational independence. This validates the platform pattern with minimal risk.

**Plan for Phase 3** after successfully onboarding 2nd operational service. ML enhancement builds on proven platform foundation.

The architecture evolution from tightly coupled operations → service separation → ML-powered orchestration represents a strategic journey from manual support to intelligent, scalable incident management.

