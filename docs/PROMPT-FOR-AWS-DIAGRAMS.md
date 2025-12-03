# Prompt for Generating AWS Architecture Diagrams

Use this prompt with **ChatGPT-4 with DALL-E** or **Microsoft Copilot** to generate professional AWS architecture diagrams.

---

## Prompt for Phase 1: Current Monolithic Architecture

```
Create a professional AWS architecture diagram for Phase 1: Current Monolithic Operations Architecture.

Use official AWS icons and follow AWS architecture diagram best practices.

Components (top to bottom):
1. User icon (operator/person)
2. Amazon API Gateway (orange icon)
3. Compute box labeled "production-support-admin" containing:
   - YAML Runbook Parser
   - Pattern Classification  
   - Entity Extraction
   - Step Orchestration
4. Arrow down to another compute box labeled "ap-services" containing:
   - Core Business Logic
   - Operational Endpoints (Mixed)
5. Arrow down to Amazon RDS/Database icon

Flow arrows:
- User → API Gateway (labeled "Operator Query")
- API Gateway → production-support-admin
- production-support-admin → ap-services (labeled "ALL operations")
- ap-services → Database

Add red warning icons on the left showing pain points:
❌ Code changes required
❌ Release cycle delays
❌ Tight coupling

Color scheme: AWS standard (orange for compute, blue for database)
Style: Clean, professional, suitable for executive presentation
Annotations: Clear labels for each component
Title: "Phase 1: Current Monolithic Operations Architecture"
```

---

## Prompt for Phase 2: Service Separation Architecture

```
Create a professional AWS architecture diagram for Phase 2: Service Separation - Platform Independence.

Use official AWS icons and follow AWS architecture diagram best practices.

Components (left to right, top to bottom):
1. User icon (operator/person)
2. Amazon API Gateway (orange icon)
3. Large compute box labeled "production-support-admin (Service-Agnostic Orchestrator)" containing:
   - YAML Runbook Parser
   - Pattern Classification
   - Entity Extraction
   - Smart Routing ⭐NEW⭐
4. Three parallel downstream services (compute boxes):
   - "connect-assist-api" (with ⭐NEW⭐ badge)
   - "datadog-api" (grayed out with "Future" label)
   - "splunk-api" (grayed out with "Future" label)
5. Below connect-assist-api: Amazon RDS/Database icon
6. To the right: Another compute box "ap-services - Core Business Only"
7. Database connects to both connect-assist-api and ap-services (shared)

Flow arrows:
- User → API Gateway (labeled "Operator Query")
- API Gateway → production-support-admin
- production-support-admin → branches to three services (labeled "Smart Routing")
- connect-assist-api → Database
- ap-services → Database (separate connection)

Add green checkmarks on the right showing benefits:
✅ Multi-team adoption
✅ Independent scaling
✅ Zero-code deployment
✅ Incident resilience

Color scheme: AWS standard (orange for compute, blue for database, green for active, gray for future)
Style: Clean, professional, suitable for executive presentation
Annotations: Clear labels for each component
Title: "Phase 2: Service Separation - Platform Independence"
```

---

## Prompt for Phase 3: ML-Powered Incident Orchestration

```
Create a professional AWS architecture diagram for Phase 3: ML-Powered Incident Orchestration.

Use official AWS icons and follow AWS architecture diagram best practices.

Components (organized in layers):

**Layer 1: User Interface**
1. User icon with speech bubble (labeled "Natural Language Query")
2. Amazon API Gateway

**Layer 2: Orchestration Platform**
3. Large compute box "production-support-admin (ML-Enhanced)" containing:
   a. Top section: "Runbook Engine"
      - YAML-driven workflows
      - Pattern matching
      - Entity extraction
   b. Bottom section: "ML Suggestion Engine" ⭐NEW⭐
      - Rule + Model inference
      - Confidence scoring
      - Historical pattern learning

**Layer 3: AWS ML Services** (minimal footprint box)
4. Grouped AWS services (light gray container labeled "AWS - Minimal Footprint"):
   - Amazon S3 icon (labeled "Incident history & Model artifacts")
   - AWS Lambda icon (labeled "Inference API (Python)")
   - Amazon SageMaker icon with "(Optional)" label (labeled "Model training")
   - Arrow showing S3 → Lambda → SageMaker relationship

**Layer 4: Downstream Services**
5. Three parallel compute boxes:
   - "connect-assist-api"
   - "datadog-api"
   - "splunk-api"
6. Databases below each service

Flow arrows:
- User → API Gateway (labeled "Natural Language")
- API Gateway → production-support-admin Runbook Engine
- Runbook Engine → ML Suggestion Engine (labeled "Incident Context")
- ML Suggestion Engine ↔ AWS Lambda (labeled "ML Recommendations < 300ms")
- Lambda → S3 (for historical data)
- Lambda → SageMaker (optional, dotted line)
- ML Suggestion Engine → three downstream services (labeled "Corrective Actions")
- production-support-admin → downstream services (labeled "Orchestrated Execution")

Add annotations:
- Shield icon near ML Suggestion Engine: "Human approval for destructive actions"
- Clock icon: "< 300ms p95 latency"
- Circuit breaker icon: "Platform continues if ML unavailable"

Add purple highlights showing ML capabilities:
🤖 Intelligent suggestions
🎯 Confidence scoring
🔄 Continuous learning
🛡️ Safety guardrails

Color scheme: 
- AWS standard (orange for compute, blue for storage/database, purple for ML)
- Green for active services
- Light gray container for AWS services (showing minimal footprint)

Style: Clean, professional, suitable for executive presentation
Annotations: Clear labels for each component
Title: "Phase 3: ML-Powered Incident Orchestration"
```

---

## Prompt for Comparison Slide

```
Create a side-by-side comparison of three architecture diagrams in simplified form, showing the evolution from Phase 1 → Phase 2 → Phase 3.

Each should be a simplified version showing only the key architectural changes:

**Phase 1** (left):
- Simple linear flow: User → API Gateway → production-support-admin → ap-services → Database
- Red "X" icons showing pain points

**Phase 2** (middle):
- User → API Gateway → production-support-admin (with "Smart Routing" label)
- Branches to three separate services (connect-assist-api, datadog-api, splunk-api)
- Green checkmarks showing benefits

**Phase 3** (right):
- User → API Gateway → production-support-admin (with "ML Engine" badge)
- ML AWS services box (S3 + Lambda + SageMaker icons in small size)
- Branches to multiple downstream services
- Purple AI/ML icons showing intelligence

Each phase labeled clearly at the top.
Arrow between phases showing progression: "→ Service Separation →" and "→ ML Enhancement →"

Style: Clean, simplified, suitable for executive summary slide
Title: "Architecture Evolution: From Monolithic to ML-Powered Platform"
```

---

## Alternative: Using draw.io or Lucidchart

If you prefer creating diagrams manually, here are the AWS icon libraries:

**draw.io (diagrams.net):**
1. Open https://app.diagrams.net/
2. Go to: More Shapes → Search "AWS"
3. Enable: "AWS 17", "AWS 19", or "AWS Architecture 2021"
4. Use the component lists above to build each phase

**Lucidchart:**
1. Open Lucidchart
2. Import AWS icon library (AWS Architecture Icons)
3. Use drag-and-drop to create diagrams following the structure above

**Key AWS Icons Needed:**
- Amazon API Gateway (orange)
- AWS Lambda (orange)
- Amazon EC2 / ECS / Compute (orange)
- Amazon RDS / Database (blue)
- Amazon S3 (green/orange)
- Amazon SageMaker (purple)
- User / Person icon
- Group containers for services

---

## Tips for Best Results

1. **Keep it clean:** Don't overcrowd diagrams
2. **Use color coding:** Active (green), Future (gray), ML (purple), AWS standard (orange/blue)
3. **Add annotations:** Labels for arrows and key components
4. **Show progression:** Make Phase 2 and Phase 3 visually build upon previous phases
5. **Highlight NEW elements:** Use ⭐ or badges to show what's new in each phase
6. **Include metrics:** Where relevant (< 300ms, ~20 ops, etc.)

---

## Deck Name Suggestions

**Option 1 (Professional):**
> "Production Support Platform: A Strategic Architecture Evolution"

**Option 2 (Technical):**
> "From Monolithic to ML-Powered: Production Support Platform Roadmap"

**Option 3 (Executive-friendly):**
> "Building a Scalable Incident Orchestration Platform: 3-Phase Journey"

**Option 4 (Concise):**
> "Platform Architecture Evolution: Monolithic → Separated → ML-Enhanced"

**Recommended:**
> "Production Support Platform Evolution: 3-Phase Architecture Roadmap"

This title is:
✅ Clear about what it is (Production Support Platform)
✅ Shows progression (Evolution)
✅ Indicates structure (3-Phase)
✅ Professional and suitable for leadership presentations

