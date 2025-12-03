# Microsoft Copilot Prompt: Generate PowerPoint Presentation

**Use this prompt with Microsoft Copilot (in PowerPoint mode) to generate the complete presentation deck.**

---

## Complete Prompt for Copilot

```
Create a professional PowerPoint presentation titled "Production Support Platform Evolution: 3-Phase Architecture Roadmap" with 15 slides. Use a modern, clean design suitable for technical leadership.

SLIDE 1: TITLE SLIDE
Title: Production Support Platform Evolution: 3-Phase Architecture Roadmap
Subtitle: From Tightly Coupled Operations to ML-Powered Incident Orchestration
Add placeholders for: [Presenter Name], [Date]

SLIDE 2: EXECUTIVE SUMMARY
Title: The Journey in 3 Phases
Content:
• Phase 1 (Today): Tightly Coupled - All operations embedded in ap-services
• Phase 2 (Near-term): Service Separation - Platform becomes service-agnostic
• Phase 3 (Future): ML-Enhanced - Intelligent incident recommendations

Key Message (in callout box): "Strategic evolution to enable multi-team adoption, independent scaling, and intelligent incident response"

SLIDE 3: CURRENT STATE & PAIN POINTS
Title: Phase 1: Current Architecture - Tightly Coupled Operations
Left side: Placeholder for architecture diagram
Right side with red X icons:
❌ Code changes required for new operations
❌ Release cycle delays - dependent on ap-services
❌ Tight coupling - operations mixed with core business
❌ Cannot scale independently
❌ Single-team usage - other teams can't adopt

Stats box at bottom:
- ~20 operational requests (steady state)
- Every new operation requires ap-services deployment
- Production support blocked by feature release cycles

SLIDE 4: PHASE 2 - SERVICE SEPARATION
Title: Phase 2: Service Separation - Platform Independence
Left side: Placeholder for architecture diagram
Right side with green checkmarks:
✅ Multi-team adoption - Platform is service-agnostic
✅ Independent scaling - Services scale based on their load
✅ Zero-code deployment - Add runbooks via YAML only
✅ Incident resilience - Orchestration stays responsive
✅ Faster operations - No ap-services dependency

Info box:
Timeline: 4-6 weeks
What is connect-assist-api?
• Exposes operational endpoints (cancel case, update status, data corrections)
• Provides data patch APIs for incident remediation
• APIs that incident smart advisor steps can call

Success Metric: Onboard 2nd service (datadog-api or splunk-api) to validate pattern

SLIDE 5: WHY SERVICE SEPARATION MATTERS
Title: Platform Adoption Requires Neutrality
Two-column comparison:

Left column - "If Operations are Embedded":
Quote box: "Team X: Can we use production-support-admin?
You: Sure, but it has LIMS stuff in it
Team X: Why do we need LIMS dependencies?"
❌ Platform becomes LIMS-specific
❌ Other teams won't adopt

Right column - "If Services are Separated":
Quote box: "Team X: Can we use production-support-admin?
You: Yes, just point your runbooks to your service
Team X: Great, we'll add datadog integration"
✅ Platform remains neutral
✅ Clean adoption path

SLIDE 6: INCIDENT SCALING IS DIFFERENT
Title: Normal Operations vs Incident Spikes
Two side-by-side sections:

Normal Operations (Steady State):
• ~20 operations
• Predictable load
• connect-assist-api handles independently

Incident Scenarios (Spike Load):
• Multiple teams trigger runbooks simultaneously
• Orchestration layer must stay responsive
• Operations shouldn't impact platform availability

Callout: "Separation enables operational resilience during high-pressure scenarios"

SLIDE 7: PHASE 3 - ML-POWERED INTELLIGENCE
Title: Phase 3: ML-Powered Incident Orchestration
Center: Placeholder for ML-enhanced architecture diagram

Key Capabilities (with icons):
🤖 Intelligent suggestions from historical incidents
🎯 Confidence scoring (high/medium/low)
🔄 Continuous learning from outcomes
🛡️ Safety guardrails - human approval for destructive actions
⚡ < 300ms p95 latency - Circuit breakers if ML unavailable

Footer: AWS Footprint: Minimal (S3 + Lambda + optional SageMaker)

SLIDE 8: ML ROLLOUT PLAN
Title: Phased ML Rollout - Learn and Adapt
Timeline graphic with 3 phases:

Phase 0: Observe Mode (4 weeks)
• Add S3 logging + telemetry
• Wire ML call without auto-apply
• Measure: ML API latency < 300ms p95

Phase 1: Safe Auto-Apply (6 weeks)
• Enable auto-apply for high-confidence, safe actions only
• Example: Status updates on LIMS
• Destructive actions require human approval

Phase 2: Expand Patterns (8 weeks)
• Add Datadog, Splunk integration patterns
• Multi-service learning
• Validate cross-service recommendations

Total Duration: 3-4 months

SLIDE 9: SAFETY & RESILIENCE
Title: Built-in Safety Mechanisms
4 quadrants layout:

1. Timeout Budget
• ML API < 300ms p95
• Orchestrator continues if ML unavailable
• No blocking dependencies

2. Circuit Breakers
• ML suggestions are advisory
• Platform remains neutral
• Graceful degradation per service

3. Guardrails
• Only 'safe_actions' auto-applied
• Destructive actions require human approval
• Audit log for all ML suggestions

4. Continuous Validation
• Feedback loop from execution outcomes
• Model retraining on historical data
• Pattern accuracy monitoring (target: 85%+)

SLIDE 10: ARCHITECTURE COMPARISON
Title: Evolution at a Glance
Placeholder for side-by-side comparison diagram

Table below:
| Aspect | Phase 1 | Phase 2 | Phase 3 |
|--------|---------|---------|---------|
| Operator Input | Manual query | Manual query | Natural language |
| Intelligence | Pattern matching | Smart routing | ML suggestions |
| Coupling | Tightly coupled | Loosely coupled | Loose + multi-service |
| Deployment | Code changes | YAML-only | YAML + ML updates |
| Teams | Single | Multi-team | Multi-team + ML |
| Incidents | Manual remediation | Orchestrated workflows | ML-suggested + orchestrated |

SLIDE 11: SUCCESS METRICS
Title: How We Measure Success
Two columns:

Phase 2 Metrics:
✅ 2nd service onboarded (validates pattern)
✅ Zero code changes for new operations
✅ Independent scaling observed
✅ Platform adoption by 2+ teams

Phase 3 Metrics:
✅ ML latency < 300ms p95
✅ 85%+ confidence for auto-applied actions
✅ 40%+ reduction in incident resolution time
✅ Multi-service pattern learning validated

Callout box:
Long-term Impact:
• Platform becomes organizational standard for operational orchestration
• Incident response time reduced significantly
• Teams can self-service operational automation

SLIDE 12: MIGRATION TIMELINE
Title: Implementation Roadmap
Gantt chart or timeline visual

Phase 1 → Phase 2 (Immediate Priority)
Duration: 4-6 weeks
Key Activities:
• Create connect-assist-api service
• Migrate 20 operational endpoints
• Add data patch APIs for incidents
• Update routing logic in production-support-admin
• Validate with existing runbooks
• Onboard 2nd service (datadog-api)
Risk: Low - YAML runbooks unchanged

Phase 2 → Phase 3 (Future Enhancement)
Duration: 3-4 months (phased)
Key Activities:
• Phase 0: Observe mode (4 weeks)
• Phase 1: Safe auto-apply (6 weeks)
• Phase 2: Expand patterns (8 weeks)
Risk: Medium - ML complexity; mitigated by circuit breakers

SLIDE 13: STRATEGIC BENEFITS
Title: Why This Matters
Two sections:

Immediate Benefits (Phase 2):
✅ Multi-team adoption enabled - Platform becomes infrastructure
✅ Operational velocity - No release cycle dependency
✅ Independent scaling - Each service optimized for its load
✅ Incident resilience - Orchestration stays available

Long-term Benefits (Phase 3):
✅ Intelligent incident response - ML learns from history
✅ Faster remediation - Context-aware suggestions
✅ Cross-service patterns - Learn from all integrations
✅ Continuous improvement - Gets smarter over time
✅ Platform differentiation - Competitive advantage

SLIDE 14: DECISION & NEXT STEPS
Title: Recommendation & Action Items

Decision box (highlighted):
"Proceed with Phase 2 immediately to unlock multi-team adoption and operational independence"

Rationale:
• Validates platform pattern with minimal risk
• Unlocks value quickly (4-6 weeks)
• Foundation for future ML enhancement

Immediate Next Steps:
1. ✅ Approve Phase 2 architecture (service separation)
2. 📋 Create connect-assist-api project
3. 👥 Assign engineering resources
4. 📅 Target completion: [Date + 6 weeks]
5. 🎯 Identify 2nd service for validation (datadog-api or splunk-api)

Phase 3 Planning:
• Begin after 2nd service onboarded successfully
• Validate multi-service pattern works
• ML enhancement builds on proven foundation

SLIDE 15: Q&A
Title: Questions & Discussion
Center text: "Questions?"

Bottom section with common questions (small font):
Prepared For:
1. Why not keep operations in ap-services?
2. Is this premature optimization?
3. What about network latency?
4. How do we handle ML failures?
5. What's the AWS cost for ML?

DESIGN SPECIFICATIONS:
• Use modern, professional template
• Color scheme: Blue/grey for corporate, green for benefits/checkmarks, red for pain points, purple for ML
• Use consistent icons throughout
• Add page numbers
• Include speaker notes on each slide with key talking points
• Make diagram placeholders prominent and labeled
• Use consistent fonts: Title (sans-serif, bold), Body (sans-serif, regular)
• Ensure high contrast for readability
```

---

## How to Use This Prompt

### Method 1: Copilot in PowerPoint (Recommended)

1. Open PowerPoint
2. Click on the **Copilot** button in the ribbon
3. Select **"Create presentation"**
4. Paste the entire prompt above
5. Click **Generate**
6. Copilot will create the full presentation

### Method 2: Copilot Web Interface

1. Go to Microsoft Copilot (copilot.microsoft.com)
2. Paste the prompt
3. Ask: "Create this as a PowerPoint presentation"
4. Download the generated .pptx file

### Method 3: Step-by-Step with Copilot

If the full prompt is too long, break it into chunks:

**First message:**
```
Create a PowerPoint presentation titled "Production Support Platform Evolution: 3-Phase Architecture Roadmap" for technical leadership. I'll provide slide-by-slide content. Start with slides 1-5 and use a modern, professional design with blue/grey color scheme.
```

**Then send slides 1-5 content, then 6-10, then 11-15**

---

## After Generation - Manual Additions

After Copilot generates the deck, you'll need to manually add:

1. **AWS Architecture Diagrams** 
   - Use the diagrams generated from PROMPT-FOR-AWS-DIAGRAMS.md
   - Insert on slides 3, 4, 7, and 10

2. **Company Branding**
   - Add your company logo to title slide
   - Adjust colors to match brand guidelines

3. **Presenter Information**
   - Fill in name and date on title slide
   - Add any company-specific information

4. **Timeline Dates**
   - Update "Target completion" date on slide 14
   - Adjust any specific dates in timeline (slide 12)

---

## Alternative: Manual Slide Creation

If Copilot doesn't work perfectly, use `/docs/SLIDE-DECK-OUTLINE.md` as your guide to create slides manually. The outline has all the content structured slide-by-slide.

---

## Tips for Best Results

1. **Be specific:** The detailed prompt above works best
2. **Generate in one go:** Better consistency than multiple requests
3. **Iterate:** If result isn't perfect, ask Copilot to "refine slide X" with specific changes
4. **Save versions:** Keep the generated deck before making major manual edits

---

## Common Copilot Follow-up Commands

After initial generation, refine with:

```
"Make slide 3 more visual with larger icons"
```

```
"Add more spacing between bullet points on slide 4"
```

```
"Make the comparison table on slide 10 easier to read"
```

```
"Add speaker notes to slide 13 with key talking points"
```

```
"Change color scheme to use more blue and less grey"
```

---

## Expected Output

You should get a complete 15-slide PowerPoint presentation with:
- ✅ Professional design and layout
- ✅ Consistent formatting
- ✅ All content from the prompt
- ✅ Placeholders for diagrams
- ✅ Icons and visual elements
- ✅ Speaker notes (if requested)

**Estimated time:** 2-3 minutes for Copilot to generate + 15-30 minutes for manual additions (diagrams, branding)

---

Good luck! The generated deck should be 90% complete and ready for your AWS diagrams. 🚀

