# Architecture Decision: Keep lims-operations-api Separate

## Decision

**Keep `lims-operations-api` as a separate service from `production-support-admin`.**

## Context

`production-support-admin` is a **multi-team operational and incident orchestration platform**. Any team should be able to add YAML-driven runbooks for incident response and troubleshooting without coupling to other teams' logic.

**Question:** Should LIMS operations be embedded in the platform or remain separate?

**Current Scale:**
- 20 LIMS operational requests (steady state, low volume)
- High potential for incident-driven spike loads across multiple teams

## Why Separation Makes Sense

### 1. Platform Adoption Requires Neutrality

**If LIMS operations are embedded:**
```
Team X: "Can we use production-support-admin?"
You: "Sure, but it has LIMS stuff in it"
Team X: "Why do we need LIMS dependencies for our runbooks?"
❌ Platform becomes LIMS-specific
❌ Other teams won't adopt
```

**If kept separate:**
```
Team X: "Can we use production-support-admin?"
You: "Yes, just point your runbooks to your service"
Team X: "Great, we'll add our datadog-api integration"
✅ Platform remains neutral
✅ Clean adoption path
```

**You're building infrastructure, not a feature service.** Embedding LIMS logic makes it LIMS-specific and prevents multi-team adoption.

### 2. Incident Scaling is Different from Operational Scaling

**Normal Operations** (steady state):
- 20 LIMS operations, predictable load
- `lims-operations-api` handles this independently

**Incident Scenarios** (spike load):
- Multiple teams trigger runbooks simultaneously
- Orchestration layer must remain responsive
- LIMS operations shouldn't impact platform availability

**During incidents:**
```
┌─────────────────────────────────┐
│  production-support-admin       │  ← Must stay responsive
│  (Orchestration Layer)          │     during incident spikes
└──────────┬──────────────────────┘
           │ Routes to...
           ├────────────────┬─────────────────┐
           ▼                ▼                 ▼
    ┌──────────────┐  ┌─────────────┐  ┌──────────┐
    │ lims-ops-api │  │ datadog-api │  │ splunk...│
    │ (may be slow)│  │             │  │          │
    └──────────────┘  └─────────────┘  └──────────┘
```

**Separation enables:**
- Independent scaling during high-pressure scenarios
- Orchestration layer stays available even if LIMS is slow
- Operational resilience, not just architectural purity

### 3. Success Metric = Multi-Team Adoption

**Platform success is measured by adoption, not by reducing service count.**

| Metric | Embedded Approach | Separated Approach |
|--------|-------------------|-------------------|
| **Team onboarding** | ❌ Inherit LIMS dependencies | ✅ Clean integration points |
| **Deployment** | ❌ Coupling across teams | ✅ Independent releases |
| **Platform identity** | ❌ LIMS-specific tool | ✅ Neutral platform |
| **Future teams** | ❌ Discouraged by coupling | ✅ Easy to adopt |

### 4. YAML-Driven Runbook Configuration Without Platform Code Changes

For platform consumers (ops teams):
- ✅ Write YAML runbooks, not Java/Go/Python
- ✅ Add new scenarios without platform team involvement
- ✅ Onboard new operational APIs independently

**This is valuable** - teams can respond to incidents without waiting for platform deployments.

## Architecture

### Recommended (Decoupled)
```
┌─────────────────────────────────┐
│  production-support-admin       │
│  • Multi-team platform          │
│  • Runbook orchestration        │
│  • Pattern matching/routing     │
│  • Independent scaling          │
└──────────┬──────────────────────┘
           │ Routes via YAML runbooks
           ├────────────────┬─────────────────┐
           ▼                ▼                 ▼
    ┌──────────────┐  ┌─────────────┐  ┌──────────┐
    │ lims-        │  │ datadog-    │  │ splunk-  │
    │ operations-  │  │ api         │  │ api      │
    │ api          │  │             │  │          │
    └──────────────┘  └─────────────┘  └──────────┘
         Team A           Team B          Team C
```

### Alternative (Embedded) - Not Recommended
```
┌──────────────────────────────────────┐
│  production-support-admin            │
│  • Contains LIMS operations          │
│  • Contains database connections     │
│  • LIMS-specific, not neutral        │
│  • Other teams discouraged           │
└──────────────────────────────────────┘
     ❌ Tightly coupled to LIMS
     ❌ Platform becomes feature service
```

## What We're Optimizing For

**NOT optimizing for:**
- Current LIMS volume (20 operations is low)
- Reducing service count
- Short-term convenience

**ARE optimizing for:**
1. Multi-team adoption
2. Incident response resilience
3. Platform independence
4. Long-term extensibility

## Failure Scenarios & Resilience

**Q: What happens when `lims-operations-api` is down during an incident?**

With separation:
- ✅ Orchestration layer remains available
- ✅ Other teams' runbooks continue working
- ✅ Can fail over to alternative operations
- ✅ Graceful degradation per service

With embedding:
- ❌ Entire platform impacted by LIMS issues
- ❌ All teams affected by one service's problems
- ❌ Cascading failures harder to contain

## Next Steps: Validate the Pattern

**Current state:** 1 operational service (LIMS)

**Next validation milestone:**
1. Onboard a second team's operational API (e.g., `datadog-api`)
2. Validate platform abstraction actually works across teams
3. Confirm runbook patterns are reusable
4. **If second integration is painful, we learn early**

Getting to 2 services proves the platform pattern. This is not premature optimization.

## Technical Concerns Addressed

### "Too Many Services?"
- Microservices are about logical boundaries, not service count
- Operational overhead is manageable with modern tooling (K8s, service mesh)
- Alternative (monolith) has worse long-term costs for multi-team adoption

### "Network Latency?"
- One extra hop adds < 1ms in same cluster
- Benefit of independent scaling outweighs minimal latency
- gRPC or service mesh can optimize if needed

### "Operational Complexity?"
- Modern platforms handle service orchestration well
- Observability tools work better with separated services
- Easier to debug and resolve isolated failures

## Conclusion

**The platform promise:**
> Multi-team operational and incident orchestration platform where any team can add runbooks without coupling to other teams' logic.

**Embedding LIMS operations breaks this by:**
- Making the platform LIMS-specific
- Preventing multi-team adoption
- Coupling incident response to LIMS availability

**Separation maintains the promise by:**
- Keeping platform neutral and adoptable
- Enabling independent scaling during incidents
- Building infrastructure, not features

---

**Recommendation:** Maintain service separation and validate the pattern by onboarding a second team's operational API.
