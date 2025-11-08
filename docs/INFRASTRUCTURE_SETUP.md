# Infrastructure Setup for production-support-admin with JWT Passthrough

> Complete AWS infrastructure setup showing JWT flow from client through API Gateway to services

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                  CLIENT                                     │
│                     (Authorization: Bearer <okta-jwt>)                      │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │ HTTPS
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           api-gateway-dev (AWS)                             │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │              okta-authorizer (Lambda Authorizer)                      │ │
│  │  • Validates JWT signature with Okta                                  │ │
│  │  • Checks token expiration                                            │ │
│  │  • Returns IAM policy + context                                       │ │
│  │  • Output: { principalId, context: { apiUser, issuer, ... } }        │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                     │                                       │
│                         ┌───────────┴───────────┐                          │
│                         │                       │                          │
│                         ▼                       ▼                          │
│         ┌───────────────────────┐   ┌──────────────────────────┐          │
│         │ Route: /ap-services/* │   │ Route: /production-      │          │
│         │                       │   │    support-admin/*       │          │
│         │ VTL Template (OLD)    │   │ VTL Template (NEW)       │          │
│         │ • NO Authorization    │   │ • ✅ Forwards            │          │
│         │ • Add Api-User        │   │   Authorization          │          │
│         │ • Add custom headers  │   │ • Add Api-User           │          │
│         │                       │   │ • Add custom headers     │          │
│         └───────────┬───────────┘   └──────────┬───────────────┘          │
└─────────────────────┼──────────────────────────┼──────────────────────────┘
                      │                          │
                      │ VPC Link                 │ VPC Link
                      ▼                          ▼
         ┌────────────────────────┐  ┌──────────────────────────┐
         │ ALB (ap-services)      │  │ ALB (prod-support-admin) │
         │ • Listener Port 443    │  │ • Listener Port 443      │
         │ • Path: /ap-services/* │  │ • Path: /production-*    │
         └────────────┬───────────┘  └──────────┬───────────────┘
                      │                         │
                      ▼                         ▼
         ┌────────────────────────┐  ┌──────────────────────────┐
         │ Target Group           │  │ Target Group             │
         │ (ap-services-tg)       │  │ (prod-support-admin-tg)  │
         │ • Health: /health      │  │ • Health: /health        │
         └────────────┬───────────┘  └──────────┬───────────────┘
                      │                         │
                      ▼                         ▼
         ┌────────────────────────┐  ┌──────────────────────────┐
         │ ECS Service            │  │ ECS Service              │
         │ (ap-services)          │  │ (prod-support-admin)     │
         │ • 2+ tasks             │  │ • 2+ tasks               │
         │ • Fargate              │  │ • Fargate                │
         └────────────────────────┘  └──────────────────────────┘
                                                 │
                                                 │ Calls ap-services
                                                 │ with same JWT
                                                 ▼
                                     ┌──────────────────────────┐
                                     │ api-gateway-dev          │
                                     │ → okta-authorizer        │
                                     │ → /ap-services/* route   │
                                     └──────────────────────────┘
```

---

## 🔄 Complete JWT Flow

### Flow 1: Direct Call to ap-services (Existing)

```
1. Client → api-gateway-dev
   Headers: Authorization: Bearer <okta-jwt>

2. api-gateway-dev → okta-authorizer (Lambda)
   • Validates JWT with Okta JWKS endpoint
   • Checks: signature, expiration, issuer, audience
   • Returns: {
       principalId: "user123",
       policyDocument: { Allow },
       context: {
         apiUser: "user123",
         email: "user@example.com",
         issuer: "https://okta.example.com",
         audience: "api-gateway-audience"
       }
     }

3. api-gateway-dev enforces IAM policy → VTL Template
   Template variables available:
   • $context.authorizer.apiUser = "user123"
   • $context.authorizer.email = "user@example.com"
   • $input.params('Authorization') = "Bearer <jwt>"
   • $input.params('Workflow-Name') = header value
   
   VTL Processing:
   #if($context.authorizer.apiUser)
   #set($apiUser = $context.authorizer.apiUser)
   #end
   
   #if($apiUser)
   #set($context.requestOverride.header.Api-User = $apiUser)
   #end
   
   # NOTE: Authorization header NOT forwarded (not in template)

4. api-gateway-dev → VPC Link → ALB → ap-services
   Headers sent to ap-services:
   • Api-User: user123
   • Workflow-Name: (if provided)
   • Lab-Id: (if provided)
   • Accession-Id: (if provided)
   • (NO Authorization header)

5. ap-services receives request
   ✅ Knows user from Api-User header
   ✅ NO JWT validation needed (already validated by API Gateway)
```

---

### Flow 2: Direct Call to production-support-admin (New)

```
1. Client → api-gateway-dev
   Headers: Authorization: Bearer <okta-jwt>

2. api-gateway-dev → okta-authorizer (Lambda)
   • Same validation as Flow 1
   • Returns policy + context with apiUser

3. api-gateway-dev enforces IAM policy → VTL Template (NEW)
   Template variables available (same as above)
   
   VTL Processing (NEW LINE ADDED):
   #if($context.authorizer.apiUser)
   #set($apiUser = $context.authorizer.apiUser)
   #end
   #set($authorization = "$input.params('Authorization')")  ← NEW
   
   #if($authorization)                                       ← NEW
   #set($context.requestOverride.header.Authorization = $authorization)  ← NEW
   #end                                                      ← NEW
   
   #if($apiUser)
   #set($context.requestOverride.header.Api-User = $apiUser)
   #end

4. api-gateway-dev → VPC Link → ALB → production-support-admin
   Headers sent to production-support-admin:
   • Authorization: Bearer <okta-jwt> ✅ (NEW)
   • Api-User: user123
   • Workflow-Name: (if provided)
   • Lab-Id: (if provided)

5. production-support-admin receives request
   ✅ Has JWT token for forwarding
   ✅ Knows user from Api-User header
```

---

### Flow 3: production-support-admin → ap-services (Chained Call)

```
1. production-support-admin decides to call ap-services
   • Extracts JWT from incoming Authorization header
   • Makes HTTP call to api-gateway-dev

2. production-support-admin → api-gateway-dev
   HTTP Request:
   POST https://api-gateway-dev.example.com/ap-services/v1/cases
   Headers:
   • Authorization: Bearer <okta-jwt> (same token from step 1)
   • Content-Type: application/json
   • Workflow-Name: (optional)

3. api-gateway-dev → okta-authorizer (Lambda)
   • JWT validated AGAIN (second validation)
   • Same checks: signature, expiration, issuer, audience
   • Returns policy + context with apiUser

4. api-gateway-dev → VTL Template (ap-services route)
   • Strips Authorization header (not forwarded)
   • Adds Api-User from context.authorizer.apiUser
   • Adds custom headers

5. api-gateway-dev → VPC Link → ALB → ap-services
   Headers sent to ap-services:
   • Api-User: user123 (extracted from JWT)
   • Workflow-Name: (if provided)
   • (NO Authorization header)

6. ap-services receives request
   ✅ Same format as Flow 1 (direct call)
   ✅ No changes needed to ap-services code
   ✅ Cannot distinguish if called directly or via production-support-admin
```

---

## 🏗️ Infrastructure Components

### 1. API Gateway (api-gateway-dev)

**Existing Component** - Minimal Changes

| Attribute | Value |
|-----------|-------|
| Name | api-gateway-dev |
| Type | REST API |
| Endpoint Type | Regional |
| Authorization | Lambda Authorizer (okta-authorizer) |

#### Existing Routes (No Changes)
```
GET    /ap-services/{proxy+}
POST   /ap-services/{proxy+}
PUT    /ap-services/{proxy+}
PATCH  /ap-services/{proxy+}
DELETE /ap-services/{proxy+}
```

#### New Routes (To Be Created)
```
GET    /production-support-admin/{proxy+}
POST   /production-support-admin/{proxy+}
PUT    /production-support-admin/{proxy+}
PATCH  /production-support-admin/{proxy+}
DELETE /production-support-admin/{proxy+}
```

**Integration Configuration (Both Routes):**
- Integration Type: `HTTP_PROXY`
- Integration Method: `ANY`
- Connection Type: `VPC_LINK`
- VPC Link: (existing - reuse)

---

### 2. Lambda Authorizer (okta-authorizer)

**Existing Component** - No Changes

| Attribute | Value |
|-----------|-------|
| Name | okta-authorizer |
| Runtime | Node.js / Python |
| Purpose | Validate Okta JWT tokens |

**Function Logic:**
```javascript
exports.handler = async (event) => {
  const token = event.authorizationToken.replace('Bearer ', '');
  
  // 1. Decode JWT
  const decoded = jwt.decode(token, { complete: true });
  
  // 2. Fetch Okta JWKS
  const jwks = await fetchJwks(decoded.header.kid);
  
  // 3. Verify signature
  const verified = jwt.verify(token, jwks.publicKey);
  
  // 4. Check expiration
  if (verified.exp < Date.now() / 1000) {
    throw new Error('Token expired');
  }
  
  // 5. Return IAM policy + context
  return {
    principalId: verified.sub,
    policyDocument: {
      Version: '2012-10-17',
      Statement: [{
        Action: 'execute-api:Invoke',
        Effect: 'Allow',
        Resource: event.methodArn
      }]
    },
    context: {
      apiUser: verified.sub,
      email: verified.email,
      issuer: verified.iss,
      audience: verified.aud
    }
  };
};
```

**Attached to Routes:**
- ✅ `/ap-services/*` (existing)
- ✅ `/production-support-admin/*` (new - same authorizer)

---

### 3. VTL Mapping Templates

#### Template A: ap-services (Existing - No Changes)

**Location:** API Gateway → `/ap-services/*` → Integration Request → Mapping Templates

```velocity
## ap-services VTL Template
## Purpose: Extract user and custom headers, DO NOT forward Authorization

#if($context.authorizer.apiUser)
#set($apiUser = $context.authorizer.apiUser)
#end
#set($workflowName = "$input.params('Workflow-Name')")
#set($accessionId = "$input.params('Accession-Id')")
#set($accessionYear = "$input.params('Accession-Year')")
#set($labId = "$input.params('Lab-Id')")
#set($disciplineName = "$input.params('Discipline-Name')")
#set($roleName = "$input.params('Role-Name')")
#set($timeZone = "$input.params('Time-Zone')")

$input.json("$")

## Add headers to forward to backend
#if($apiUser)
#set($context.requestOverride.header.Api-User = $apiUser)
#end
#if($workflowName)
#set($context.requestOverride.header.Workflow-Name = $workflowName)
#end
#if($labId)
#set($context.requestOverride.header.Lab-Id = $labId)
#end
#if($accessionId)
#set($context.requestOverride.header.Accession-Id = $accessionId)
#end
#if($accessionYear)
#set($context.requestOverride.header.Accession-Year = $accessionYear)
#end
#if($disciplineName)
#set($context.requestOverride.header.Discipline-Name = $disciplineName)
#end
#if($roleName)
#set($context.requestOverride.header.Role-Name = $roleName)
#end
#if($timeZone)
#set($context.requestOverride.header.Time-Zone = $timeZone)
#end

## Forward query parameters
#foreach($param in $input.params().querystring.keySet())
#set($context.requestOverride.querystring[$param]= $input.params().querystring.get($param))
#if($foreach.hasNext) #end
#end
```

**Key Point:** Authorization header is NOT extracted → NOT forwarded

---

#### Template B: production-support-admin (NEW - To Be Created)

**Location:** API Gateway → `/production-support-admin/*` → Integration Request → Mapping Templates

```velocity
## production-support-admin VTL Template
## Purpose: Forward Authorization header + extract user and custom headers

#if($context.authorizer.apiUser)
#set($apiUser = $context.authorizer.apiUser)
#end
#set($authorization = "$input.params('Authorization')")  ## ← KEY ADDITION
#set($workflowName = "$input.params('Workflow-Name')")
#set($accessionId = "$input.params('Accession-Id')")
#set($accessionYear = "$input.params('Accession-Year')")
#set($labId = "$input.params('Lab-Id')")
#set($disciplineName = "$input.params('Discipline-Name')")
#set($roleName = "$input.params('Role-Name')")
#set($timeZone = "$input.params('Time-Zone')")

$input.json("$")

## KEY ADDITION: Forward Authorization header
#if($authorization)
#set($context.requestOverride.header.Authorization = $authorization)
#end

## Add other headers to forward to backend (same as ap-services)
#if($apiUser)
#set($context.requestOverride.header.Api-User = $apiUser)
#end
#if($workflowName)
#set($context.requestOverride.header.Workflow-Name = $workflowName)
#end
#if($labId)
#set($context.requestOverride.header.Lab-Id = $labId)
#end
#if($accessionId)
#set($context.requestOverride.header.Accession-Id = $accessionId)
#end
#if($accessionYear)
#set($context.requestOverride.header.Accession-Year = $accessionYear)
#end
#if($disciplineName)
#set($context.requestOverride.header.Discipline-Name = $disciplineName)
#end
#if($roleName)
#set($context.requestOverride.header.Role-Name = $roleName)
#end
#if($timeZone)
#set($context.requestOverride.header.Time-Zone = $timeZone)
#end

## Forward query parameters
#foreach($param in $input.params().querystring.keySet())
#set($context.requestOverride.querystring[$param]= $input.params().querystring.get($param))
#if($foreach.hasNext) #end
#end
```

**Difference:** ONE addition - `#set($authorization = ...)` and conditional forwarding

---

### 4. VPC Link

**Existing Component** - Reuse

| Attribute | Value |
|-----------|-------|
| Name | api-gateway-vpc-link |
| Type | VPC Link (for REST APIs) |
| Target | Network Load Balancer |
| Purpose | Connect API Gateway to private VPC resources |

**Used By:**
- ✅ ap-services route (existing)
- ✅ production-support-admin route (new - reuse same VPC Link)

**Configuration:**
- Target NLB: Points to ALBs in private subnets
- Security: API Gateway can reach private resources

---

### 5. Application Load Balancers (ALB)

#### ALB Option A: Reuse Existing ALB (Recommended)

**If using same ALB as ap-services:**

| Attribute | Value |
|-----------|-------|
| Name | app-services-alb |
| Scheme | internal |
| Subnets | Private subnets (2+ AZs) |
| Security Group | alb-security-group |

**Listener: HTTPS (Port 443)**

| Priority | Path Pattern | Target Group | Action |
|----------|-------------|--------------|--------|
| 1 | `/ap-services/*` | ap-services-tg | Forward |
| 2 | `/production-support-admin/*` | production-support-admin-tg | Forward (NEW) |

**Security Group (alb-security-group):**
```
Inbound Rules:
- Port 443 from VPC Link / API Gateway CIDR
- Port 443 from production-support-admin ECS tasks (for calling ap-services)

Outbound Rules:
- Port 8093 to ap-services-ecs-sg
- Port 8093 to production-support-admin-ecs-sg
```

---

#### ALB Option B: Create New ALB (Alternative)

**If creating dedicated ALB for production-support-admin:**

| Attribute | Value |
|-----------|-------|
| Name | production-support-admin-alb |
| Scheme | internal |
| Subnets | Private subnets (2+ AZs) |
| Security Group | production-support-admin-alb-sg |

**Listener: HTTPS (Port 443)**

| Path Pattern | Target Group | Action |
|-------------|--------------|--------|
| `/*` | production-support-admin-tg | Forward |

---

### 6. Target Groups

#### Target Group A: ap-services (Existing - No Changes)

| Attribute | Value |
|-----------|-------|
| Name | ap-services-tg |
| Target Type | IP |
| Protocol | HTTP |
| Port | 8080 (or whatever ap-services uses) |
| VPC | same-vpc |
| Health Check Path | `/health` or `/actuator/health` |
| Health Check Interval | 30 seconds |
| Healthy Threshold | 2 |
| Unhealthy Threshold | 3 |

---

#### Target Group B: production-support-admin (NEW)

| Attribute | Value |
|-----------|-------|
| Name | production-support-admin-tg |
| Target Type | IP |
| Protocol | HTTP |
| Port | 8093 |
| VPC | same-vpc |
| Health Check Path | `/api/v1/health` |
| Health Check Interval | 30 seconds |
| Healthy Threshold | 2 |
| Unhealthy Threshold | 3 |
| Deregistration Delay | 30 seconds |

**Targets:** ECS tasks from production-support-admin service (auto-registered)

---

### 7. ECS Infrastructure

#### ECS Cluster

**Existing Component** - Can Reuse or Create New

| Attribute | Value |
|-----------|-------|
| Name | app-services-cluster (or new) |
| Type | Fargate |
| Container Insights | Enabled |

---

#### ECS Service A: ap-services (Existing - No Changes)

| Attribute | Value |
|-----------|-------|
| Service Name | ap-services |
| Cluster | app-services-cluster |
| Launch Type | FARGATE |
| Desired Count | 2 |
| Task Definition | ap-services:latest |

---

#### ECS Service B: production-support-admin (NEW)

| Attribute | Value |
|-----------|-------|
| Service Name | production-support-admin |
| Cluster | app-services-cluster |
| Launch Type | FARGATE |
| Platform Version | LATEST |
| Desired Count | 2 |
| Task Definition | production-support-admin:latest |
| Deployment Type | Rolling Update |
| Min Healthy % | 100 |
| Max Healthy % | 200 |

**Network Configuration:**
```yaml
VPC: same-vpc
Subnets: [private-subnet-1, private-subnet-2]
Security Groups: [production-support-admin-ecs-sg]
Assign Public IP: DISABLED
```

**Load Balancer Configuration:**
```yaml
Target Group: production-support-admin-tg
Container Name: production-support-admin
Container Port: 8093
```

**Service Discovery:** (Optional)
- Can register in AWS Cloud Map for service-to-service discovery

---

### 8. ECS Task Definitions

#### Task Definition: production-support-admin (NEW)

```yaml
Family: production-support-admin
Network Mode: awsvpc
Requires Compatibilities: [FARGATE]
CPU: 512 (0.5 vCPU)
Memory: 1024 (1 GB)
Task Execution Role: arn:aws:iam::<account>:role/production-support-admin-execution-role
Task Role: arn:aws:iam::<account>:role/production-support-admin-task-role

Container Definitions:
  - Name: production-support-admin
    Image: <account>.dkr.ecr.<region>.amazonaws.com/production-support-admin:latest
    Essential: true
    Port Mappings:
      - Container Port: 8093
        Protocol: tcp
    
    Environment Variables:
      - SPRING_PROFILES_ACTIVE: prod
      - SERVER_PORT: 8093
      - DOWNSTREAM_SERVICES_SERVICES_AP_SERVICES_BASE_URL: https://api-gateway-dev.example.com
      - DOWNSTREAM_SERVICES_SERVICES_AP_SERVICES_TIMEOUT: 30
    
    Logging:
      Log Driver: awslogs
      Options:
        awslogs-group: /ecs/production-support-admin
        awslogs-region: us-east-1
        awslogs-stream-prefix: ecs
    
    Health Check:
      Command: ["CMD-SHELL", "curl -f http://localhost:8093/api/v1/health || exit 1"]
      Interval: 30
      Timeout: 5
      Retries: 3
      Start Period: 60
```

---

### 9. Security Groups

#### Security Group A: ALB (Existing or Updated)

**Name:** `alb-security-group` or `app-services-alb-sg`

```yaml
Inbound Rules:
  - Type: HTTPS
    Port: 443
    Source: VPC Link / API Gateway ENIs
    Description: Allow HTTPS from API Gateway
  
  - Type: HTTPS
    Port: 443
    Source: production-support-admin-ecs-sg  # NEW
    Description: Allow production-support-admin to call ap-services

Outbound Rules:
  - Type: Custom TCP
    Port: 8080
    Destination: ap-services-ecs-sg
    Description: Forward to ap-services
  
  - Type: Custom TCP
    Port: 8093
    Destination: production-support-admin-ecs-sg  # NEW
    Description: Forward to production-support-admin
```

---

#### Security Group B: production-support-admin ECS (NEW)

**Name:** `production-support-admin-ecs-sg`

```yaml
Inbound Rules:
  - Type: Custom TCP
    Port: 8093
    Source: alb-security-group
    Description: Allow traffic from ALB

Outbound Rules:
  - Type: HTTPS
    Port: 443
    Destination: 0.0.0.0/0
    Description: Call API Gateway for ap-services
  
  - Type: HTTPS
    Port: 443
    Destination: <api-gateway-vpc-endpoint-sg>
    Description: Call API Gateway via VPC endpoint (if using)
```

---

### 10. IAM Roles

#### Role A: ECS Task Execution Role (NEW)

**Name:** `production-support-admin-execution-role`

**Purpose:** Pull container images, write logs, access secrets

**Trust Policy:**
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Service": "ecs-tasks.amazonaws.com"
    },
    "Action": "sts:AssumeRole"
  }]
}
```

**Attached Policies:**
- `AmazonECSTaskExecutionRolePolicy` (AWS managed)
- Custom policy for ECR:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:log-group:/ecs/production-support-admin:*"
    }
  ]
}
```

---

#### Role B: ECS Task Role (NEW)

**Name:** `production-support-admin-task-role`

**Purpose:** Permissions for application runtime (AWS SDK calls)

**Trust Policy:**
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Service": "ecs-tasks.amazonaws.com"
    },
    "Action": "sts:AssumeRole"
  }]
}
```

**Attached Policies:**
- Minimal initially, add as needed
- Example: S3 access, DynamoDB access, etc.

---

### 11. CloudWatch Logs

#### Log Group: production-support-admin (NEW)

| Attribute | Value |
|-----------|-------|
| Name | `/ecs/production-support-admin` |
| Retention | 30 days |
| KMS Encryption | (Optional) |

**Log Streams:** Auto-created by ECS (one per task)

**Example Log Stream Name:**
```
ecs/production-support-admin/a1b2c3d4-5678-90ab-cdef-1234567890ab
```

---

### 12. CloudWatch Alarms

#### Alarm Set A: ECS Service Health

```yaml
Alarm: production-support-admin-cpu-high
  Metric: CPUUtilization
  Namespace: AWS/ECS
  Dimensions:
    ServiceName: production-support-admin
    ClusterName: app-services-cluster
  Threshold: > 80%
  Period: 300 seconds (5 min)
  Evaluation Periods: 2
  Action: SNS notification

Alarm: production-support-admin-memory-high
  Metric: MemoryUtilization
  Namespace: AWS/ECS
  Dimensions:
    ServiceName: production-support-admin
    ClusterName: app-services-cluster
  Threshold: > 80%
  Period: 300 seconds
  Evaluation Periods: 2
  Action: SNS notification

Alarm: production-support-admin-task-count-low
  Metric: RunningTaskCount
  Namespace: ECS/ContainerInsights
  Dimensions:
    ServiceName: production-support-admin
    ClusterName: app-services-cluster
  Threshold: < 1
  Period: 60 seconds
  Evaluation Periods: 1
  Action: SNS notification
```

#### Alarm Set B: Target Group Health

```yaml
Alarm: production-support-admin-unhealthy-hosts
  Metric: UnHealthyHostCount
  Namespace: AWS/ApplicationELB
  Dimensions:
    TargetGroup: production-support-admin-tg
    LoadBalancer: app-services-alb
  Threshold: > 0
  Period: 120 seconds
  Evaluation Periods: 2
  Action: SNS notification

Alarm: production-support-admin-response-time-high
  Metric: TargetResponseTime
  Namespace: AWS/ApplicationELB
  Dimensions:
    TargetGroup: production-support-admin-tg
  Statistic: p99
  Threshold: > 3000 ms
  Period: 300 seconds
  Evaluation Periods: 1
  Action: SNS notification
```

#### Alarm Set C: API Gateway

```yaml
Alarm: production-support-admin-5xx-errors
  Metric: 5XXError
  Namespace: AWS/ApiGateway
  Dimensions:
    ApiName: api-gateway-dev
    Stage: prod
    Resource: /production-support-admin/{proxy+}
  Threshold: > 10 (count)
  Period: 300 seconds
  Evaluation Periods: 1
  Action: SNS notification

Alarm: production-support-admin-latency-high
  Metric: Latency
  Namespace: AWS/ApiGateway
  Dimensions:
    ApiName: api-gateway-dev
    Stage: prod
    Resource: /production-support-admin/{proxy+}
  Statistic: p99
  Threshold: > 5000 ms
  Period: 300 seconds
  Evaluation Periods: 1
  Action: SNS notification
```

---

### 13. Container Registry (ECR)

#### ECR Repository (NEW)

| Attribute | Value |
|-----------|-------|
| Name | production-support-admin |
| Tag Immutability | Disabled |
| Scan on Push | Enabled |
| Encryption | AES-256 |

**Lifecycle Policy:**
```json
{
  "rules": [{
    "rulePriority": 1,
    "description": "Keep last 10 images",
    "selection": {
      "tagStatus": "any",
      "countType": "imageCountMoreThan",
      "countNumber": 10
    },
    "action": {
      "type": "expire"
    }
  }]
}
```

**Repository URL:**
```
<account-id>.dkr.ecr.<region>.amazonaws.com/production-support-admin
```

---

## 📋 What Gets Created vs Updated

### Created (New Resources)

| Component | Name | Type |
|-----------|------|------|
| ✅ API Gateway Routes | `/production-support-admin/*` | Resource |
| ✅ VTL Mapping Template | production-support-admin template | Configuration |
| ✅ Target Group | production-support-admin-tg | ALB Resource |
| ✅ ECS Service | production-support-admin | ECS Resource |
| ✅ ECS Task Definition | production-support-admin:1 | ECS Resource |
| ✅ Security Group | production-support-admin-ecs-sg | VPC Resource |
| ✅ IAM Role | production-support-admin-execution-role | IAM Resource |
| ✅ IAM Role | production-support-admin-task-role | IAM Resource |
| ✅ CloudWatch Log Group | /ecs/production-support-admin | Logs Resource |
| ✅ CloudWatch Alarms | (multiple) | Monitoring |
| ✅ ECR Repository | production-support-admin | Container Registry |

---

### Updated (Existing Resources)

| Component | Change | Impact |
|-----------|--------|--------|
| ALB Listener | Add rule for `/production-support-admin/*` | Low - additive only |
| ALB Security Group | Add outbound rule to new ECS SG | Low - additive only |

---

### Unchanged (Reused As-Is)

| Component | Purpose |
|-----------|---------|
| api-gateway-dev | Add routes only |
| okta-authorizer | Reuse for new routes |
| VPC Link | Reuse for new integration |
| ECS Cluster | Can host both services |
| VPC, Subnets | Network infrastructure |
| ap-services | No changes at all |

---

## 🔒 Security Validation Checklist

### JWT Security

- [ ] JWT validated twice: Once for production-support-admin, once for ap-services
- [ ] Same Okta JWKS endpoint used for validation
- [ ] Token expiration checked at each validation
- [ ] Signature verification with public key
- [ ] Issuer and audience claims validated

### Network Security

- [ ] All traffic encrypted (HTTPS/TLS)
- [ ] ECS tasks in private subnets (no public IPs)
- [ ] Security groups follow least privilege
- [ ] Only required ports open (443, 8093)
- [ ] VPC Link used for API Gateway → VPC communication

### IAM Security

- [ ] ECS task roles have minimal permissions
- [ ] Execution role limited to ECR and CloudWatch
- [ ] No hardcoded credentials in environment variables
- [ ] Use Secrets Manager for sensitive config (if needed)

### Monitoring & Compliance

- [ ] CloudWatch Logs enabled for audit trail
- [ ] Request IDs tracked through system
- [ ] Alarms configured for security events
- [ ] Failed auth attempts logged

---

## 🧪 Testing Scenarios

### Test 1: Direct Call to ap-services (Should Work - No Changes)

```bash
# Get Okta JWT token
JWT="<valid-okta-jwt>"

# Call ap-services directly
curl -v https://api-gateway-dev.example.com/ap-services/v1/cases \
  -H "Authorization: Bearer $JWT" \
  -H "Workflow-Name: test-workflow"

# Expected:
# - 200 OK
# - ap-services receives: Api-User, Workflow-Name
# - ap-services does NOT receive: Authorization header
```

### Test 2: Direct Call to production-support-admin

```bash
# Call production-support-admin
curl -v https://api-gateway-dev.example.com/production-support-admin/api/v1/health \
  -H "Authorization: Bearer $JWT"

# Expected:
# - 200 OK
# - production-support-admin receives: Authorization, Api-User
# - Check logs: Authorization header present
```

### Test 3: Chained Call (production-support-admin → ap-services)

```bash
# Call production-support-admin which then calls ap-services
curl -v https://api-gateway-dev.example.com/production-support-admin/api/v1/process \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"query": "cancel case 2025123P6732"}'

# Expected:
# - 200 OK from production-support-admin
# - production-support-admin makes request to api-gateway-dev/ap-services
# - JWT validated again by okta-authorizer
# - ap-services receives: Api-User header (same format as Test 1)
# - Check logs: JWT forwarded, validated twice
```

### Test 4: Expired JWT (Should Fail)

```bash
# Use expired JWT
EXPIRED_JWT="<expired-jwt>"

curl -v https://api-gateway-dev.example.com/production-support-admin/api/v1/health \
  -H "Authorization: Bearer $EXPIRED_JWT"

# Expected:
# - 401 Unauthorized
# - okta-authorizer rejects token
# - Error: "Token expired"
```

### Test 5: No JWT (Should Fail)

```bash
# Call without Authorization header
curl -v https://api-gateway-dev.example.com/production-support-admin/api/v1/health

# Expected:
# - 401 Unauthorized
# - okta-authorizer rejects request
```

---

## 📊 Key Metrics to Monitor

### ECS Metrics

| Metric | Threshold | Alert If |
|--------|-----------|----------|
| CPUUtilization | 80% | Above for 5 min |
| MemoryUtilization | 80% | Above for 5 min |
| RunningTaskCount | 1 | Below |

### ALB Metrics

| Metric | Threshold | Alert If |
|--------|-----------|----------|
| HealthyHostCount | 1 | Below |
| TargetResponseTime (p99) | 3s | Above for 5 min |
| RequestCount | - | Track trend |

### API Gateway Metrics

| Metric | Threshold | Alert If |
|--------|-----------|----------|
| 4XXError | 5% | Above |
| 5XXError | 1% | Above |
| Latency (p99) | 5s | Above |
| Count | - | Track trend |

### Application Metrics (Custom)

| Metric | Purpose |
|--------|---------|
| JWT validations/minute | Track auth load |
| Downstream calls/minute | Track ap-services calls |
| Cache hit rate | If caching implemented |

---

## 🎯 Summary

### Key Points

1. **JWT Validated Twice:**
   - Once for production-support-admin route
   - Once when production-support-admin calls ap-services route
   - Same validation logic, same okta-authorizer

2. **ap-services Unchanged:**
   - Always receives same headers (Api-User + custom)
   - Cannot tell if called directly or via production-support-admin
   - No code changes needed

3. **Minimal Infrastructure Changes:**
   - 95% reuse of existing pattern
   - ONE line added to VTL template
   - New ECS service + supporting resources

4. **Security Maintained:**
   - All requests authenticated
   - JWT never exposed to ap-services (not needed)
   - Same security posture as existing setup

5. **Scalability:**
   - Both services scale independently
   - ALB distributes load
   - Auto-scaling can be added

---

## 📚 Additional Resources

- [AWS API Gateway VTL Reference](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-mapping-template-reference.html)
- [Lambda Authorizer Documentation](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-use-lambda-authorizer.html)
- [ECS Fargate Networking](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-networking.html)
- [VPC Link for API Gateway](https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-private-integration.html)

