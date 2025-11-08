# Infrastructure Story: production-support-admin Service Setup

---

## 📋 JIRA Story

### Story ID
`INFRA-XXX`

### Story Title
**Set up AWS infrastructure for production-support-admin service with JWT passthrough**

### Story Type
Infrastructure / DevOps

### Priority
High

### Story Points
13

---

## 📝 Story Description

Provision complete AWS infrastructure for the new **production-support-admin** microservice. This service will receive authenticated requests via our existing API Gateway (api-gateway-dev) and forward Okta JWT tokens to downstream services (specifically ap-services).

**Key Requirement:** Unlike ap-services which only receives `Api-User` header, production-support-admin must receive the full JWT token via `Authorization: Bearer <jwt>` header so it can forward it to downstream services.

**Architecture:**
```
Client → api-gateway-dev → okta-authorizer → production-support-admin (receives JWT)
                                                        ↓
                                              api-gateway-dev → ap-services
```

---

## ✅ Acceptance Criteria

### Functional
- [ ] production-support-admin routes accessible via api-gateway-dev at `/production-support-admin/*`
- [ ] Requests authenticated via existing okta-authorizer Lambda
- [ ] Service receives both `Authorization: Bearer <jwt>` AND `Api-User` headers
- [ ] Service can successfully call ap-services through api-gateway-dev using forwarded JWT
- [ ] Health check endpoint `/health` returns 200 OK

### Infrastructure
- [ ] ECS service running with minimum 2 healthy tasks
- [ ] ALB health checks passing (healthy threshold met)
- [ ] API Gateway integration returns 2xx for valid authenticated requests
- [ ] CloudWatch logs capturing request/response flow
- [ ] Security groups configured with least privilege

### Testing
- [ ] Smoke test: `GET /production-support-admin/health` returns 200
- [ ] Auth test: Request with valid JWT succeeds
- [ ] Auth test: Request without JWT returns 401
- [ ] Integration test: production-support-admin → ap-services call works
- [ ] Logs show Authorization header being received

---

## 🎯 Technical Implementation Details

### Infrastructure Components to Create

| Component | Action | Reuse from ap-services |
|-----------|--------|----------------------|
| API Gateway Routes | Create new | ✅ Use existing gateway |
| VTL Template | Create new | ✅ Based on ap-services + Authorization |
| ECS Service | Create new | ✅ Use pattern |
| ECS Task Definition | Create new | ✅ Use pattern |
| Target Group | Create new | ✅ Attach to existing ALB |
| Security Groups | Create new | ✅ Copy pattern |
| IAM Roles | Create new | ✅ Copy pattern |
| ECR Repository | Create new | - |
| CloudWatch Logs | Create new | ✅ Use pattern |

### Key Configuration Changes

#### VTL Template (NEW - Based on ap-services)
**Add ONE line to forward Authorization header:**
```velocity
#set($authorization = "$input.params('Authorization')")
...
#if($authorization)
#set($context.requestOverride.header.Authorization = $authorization)
#end
```

#### Environment Variables for ECS Task
```bash
SPRING_PROFILES_ACTIVE=prod
DOWNSTREAM_SERVICES_SERVICES_AP_SERVICES_BASE_URL=https://api-gateway-dev.example.com/ap-services
DOWNSTREAM_SERVICES_SERVICES_AP_SERVICES_TIMEOUT=30
SERVER_PORT=8093
```

---

## 📦 Subtasks

### Task 1: ECR Repository Setup
**Assignee:** DevOps  
**Estimated:** 0.5h

**Description:**
Create ECR repository for production-support-admin Docker images

**Steps:**
1. Create ECR repository: `production-support-admin`
2. Configure lifecycle policy (keep last 10 images)
3. Enable scan on push
4. Set permissions for ECS to pull images
5. Document repository URL

**Acceptance:**
- [ ] ECR repository exists and is accessible
- [ ] Lifecycle policy configured
- [ ] Test image can be pushed and pulled

---

### Task 2: IAM Roles Creation
**Assignee:** DevOps  
**Estimated:** 1h

**Description:**
Create IAM roles for ECS task execution and task runtime

**Steps:**
1. **Create ECS Task Execution Role** (copy from ap-services):
   - Name: `production-support-admin-execution-role`
   - Permissions: ECR pull, CloudWatch Logs write, Secrets Manager read
   
2. **Create ECS Task Role** (copy from ap-services):
   - Name: `production-support-admin-task-role`
   - Permissions: (minimal initially, add as needed)
   - Add permission to call API Gateway if needed

3. Document role ARNs

**Acceptance:**
- [ ] Both IAM roles created
- [ ] Trust policies configured for ECS
- [ ] Permissions validated

---

### Task 3: Security Groups Configuration
**Assignee:** DevOps/Network  
**Estimated:** 1h

**Description:**
Create security groups for ALB and ECS tasks

**Steps:**
1. **Create/Update ALB Security Group:**
   - Inbound: Port 443 from VPC Link/API Gateway
   - Outbound: Port 8093 to ECS security group

2. **Create ECS Task Security Group:**
   - Name: `production-support-admin-ecs-sg`
   - Inbound: Port 8093 from ALB security group
   - Outbound: 
     - Port 443 to api-gateway-dev (for calling ap-services)
     - Port 443 to internet (for other services if needed)

3. Document security group IDs

**Acceptance:**
- [ ] Security groups created
- [ ] Rules configured with least privilege
- [ ] Security groups documented

---

### Task 4: Target Group & ALB Configuration
**Assignee:** DevOps  
**Estimated:** 1h

**Description:**
Create target group and configure ALB listener rules

**Steps:**
1. **Create Target Group:**
   - Name: `production-support-admin-tg`
   - Protocol: HTTP
   - Port: 8093
   - VPC: (same as ap-services)
   - Health check path: `/api/v1/health`
   - Health check interval: 30s
   - Healthy threshold: 2
   - Unhealthy threshold: 3
   - Timeout: 5s

2. **Add Listener Rule to ALB:**
   - Condition: Path pattern `/production-support-admin/*`
   - Action: Forward to `production-support-admin-tg`
   - Priority: (set appropriately)

3. Document ALB DNS name and target group ARN

**Acceptance:**
- [ ] Target group created and healthy
- [ ] Listener rule configured
- [ ] Health checks defined correctly

---

### Task 5: ECS Task Definition
**Assignee:** DevOps  
**Estimated:** 2h

**Description:**
Create ECS task definition for production-support-admin

**Steps:**
1. **Create Task Definition:**
   - Family: `production-support-admin`
   - Network mode: awsvpc
   - Requires compatibilities: FARGATE
   - CPU: 512 (0.5 vCPU) - adjust as needed
   - Memory: 1024 (1 GB) - adjust as needed
   - Task execution role: (from Task 2)
   - Task role: (from Task 2)

2. **Container Definition:**
   - Container name: `production-support-admin`
   - Image: `<account-id>.dkr.ecr.<region>.amazonaws.com/production-support-admin:latest`
   - Port mappings: 8093
   - Environment variables:
     ```
     SPRING_PROFILES_ACTIVE=prod
     DOWNSTREAM_SERVICES_SERVICES_AP_SERVICES_BASE_URL=https://api-gateway-dev.example.com
     DOWNSTREAM_SERVICES_SERVICES_AP_SERVICES_TIMEOUT=30
     SERVER_PORT=8093
     ```
   - Logging:
     - Driver: awslogs
     - Log group: `/ecs/production-support-admin`
     - Region: <region>
     - Stream prefix: ecs

3. Register task definition
4. Document task definition ARN

**Acceptance:**
- [ ] Task definition created and registered
- [ ] Container configuration validated
- [ ] Environment variables set correctly
- [ ] Logging configured

---

### Task 6: CloudWatch Logs Setup
**Assignee:** DevOps  
**Estimated:** 0.5h

**Description:**
Create CloudWatch log group for production-support-admin

**Steps:**
1. Create log group: `/ecs/production-support-admin`
2. Set retention policy: 30 days
3. Configure permissions for ECS to write logs
4. Test log streaming

**Acceptance:**
- [ ] Log group exists
- [ ] Retention configured
- [ ] Logs are being written

---

### Task 7: ECS Service Creation
**Assignee:** DevOps  
**Estimated:** 1.5h

**Description:**
Create ECS service for production-support-admin

**Steps:**
1. **Create ECS Service:**
   - Service name: `production-support-admin`
   - Cluster: (existing or new)
   - Launch type: FARGATE
   - Task definition: (from Task 5)
   - Desired count: 2
   - Deployment type: Rolling update
   - Minimum healthy: 100%
   - Maximum healthy: 200%

2. **Network Configuration:**
   - VPC: (same as ap-services)
   - Subnets: (private subnets)
   - Security group: (from Task 3)
   - Public IP: Disabled

3. **Load Balancer Configuration:**
   - Target group: (from Task 4)
   - Container port: 8093

4. **Health Check Grace Period:** 60 seconds

5. Enable ECS Exec for debugging (optional)

**Acceptance:**
- [ ] ECS service created
- [ ] Service shows 2 running tasks
- [ ] Tasks registered with target group
- [ ] Target group shows healthy targets

---

### Task 8: API Gateway Route Configuration
**Assignee:** DevOps/API Team  
**Estimated:** 2h

**Description:**
Configure API Gateway routes for production-support-admin with JWT passthrough

**Steps:**
1. **Create Resource:**
   - Path: `/production-support-admin`
   - Create child resource: `{proxy+}`

2. **Configure Integration:**
   - Integration type: HTTP_PROXY
   - Integration HTTP method: ANY
   - Endpoint URL: `http://<alb-dns>/production-support-admin/{proxy}`
   - VPC Link: (existing)

3. **Configure Method (ANY):**
   - Authorization: okta-authorizer (existing)
   - API Key Required: No
   - Request Validator: None

4. **Create VTL Mapping Template:**
   ```velocity
   ## Based on ap-services template with Authorization forwarding
   #if($context.authorizer.apiUser)
   #set($apiUser = $context.authorizer.apiUser)
   #end
   #set($authorization = "$input.params('Authorization')")
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

   #foreach($param in $input.params().querystring.keySet())
   #set($context.requestOverride.querystring[$param]= $input.params().querystring.get($param))
   #if($foreach.hasNext) #end
   #end
   ```

5. Deploy API Gateway changes to dev stage

6. Document API Gateway endpoint

**Acceptance:**
- [ ] Routes created in API Gateway
- [ ] VTL template configured with Authorization forwarding
- [ ] Authorization configured with okta-authorizer
- [ ] Changes deployed to dev stage
- [ ] API Gateway endpoint documented

---

### Task 9: CloudWatch Alarms Configuration
**Assignee:** DevOps  
**Estimated:** 1.5h

**Description:**
Set up CloudWatch alarms for monitoring

**Steps:**
1. **ECS Service Alarms:**
   - CPU Utilization > 80% for 5 minutes
   - Memory Utilization > 80% for 5 minutes
   - Running task count < 1

2. **ALB Target Group Alarms:**
   - Unhealthy host count > 0 for 2 minutes
   - Target response time > 3 seconds

3. **API Gateway Alarms:**
   - 5xx error rate > 5% for 5 minutes
   - Latency > 5 seconds (p99)

4. Configure SNS topic for alarm notifications
5. Test alarm triggering

**Acceptance:**
- [ ] All alarms created
- [ ] SNS topic configured
- [ ] Test alarm triggered successfully
- [ ] Notifications received

---

### Task 10: Integration & Smoke Testing
**Assignee:** DevOps + QA  
**Estimated:** 3h

**Description:**
Validate complete infrastructure setup with comprehensive testing

**Test Cases:**

#### 1. Health Check Test
```bash
# Without authentication (should fail)
curl -v https://api-gateway-dev.example.com/production-support-admin/api/v1/health

# Expected: 401 Unauthorized

# With valid JWT
curl -v -H "Authorization: Bearer <valid-okta-jwt>" \
  https://api-gateway-dev.example.com/production-support-admin/api/v1/health

# Expected: 200 OK
```

#### 2. Header Propagation Test
```bash
# Call endpoint that logs received headers
curl -H "Authorization: Bearer <valid-jwt>" \
     -H "Workflow-Name: test-workflow" \
     -H "Lab-Id: lab123" \
  https://api-gateway-dev.example.com/production-support-admin/api/v1/debug/headers

# Expected: Logs show Authorization, Api-User, Workflow-Name, Lab-Id headers
```

#### 3. JWT Forwarding Test
```bash
# Call endpoint that forwards request to ap-services
curl -X POST \
  -H "Authorization: Bearer <valid-jwt>" \
  -H "Content-Type: application/json" \
  -d '{"query": "test case request"}' \
  https://api-gateway-dev.example.com/production-support-admin/api/v1/process

# Expected: 
# - Request succeeds
# - Logs show JWT being forwarded to ap-services
# - ap-services receives request with Api-User header
```

#### 4. Load Test (Basic)
```bash
# Use Apache Bench or similar
ab -n 1000 -c 10 \
  -H "Authorization: Bearer <valid-jwt>" \
  https://api-gateway-dev.example.com/production-support-admin/api/v1/health

# Expected:
# - No failures
# - Average response time < 500ms
# - ECS tasks remain healthy
```

#### 5. CloudWatch Logs Validation
- [ ] Check `/ecs/production-support-admin` for application logs
- [ ] Verify structured logging is working
- [ ] Check API Gateway logs for request flow
- [ ] Verify all headers are being passed

#### 6. Security Validation
- [ ] Verify requests without JWT are rejected
- [ ] Verify expired JWT is rejected
- [ ] Verify invalid JWT is rejected
- [ ] Verify security groups allow only required traffic

**Acceptance:**
- [ ] All test cases pass
- [ ] No errors in CloudWatch logs
- [ ] ECS tasks remain healthy throughout tests
- [ ] Target group maintains healthy status
- [ ] API Gateway metrics show successful requests

---

### Task 11: Documentation & Handoff
**Assignee:** DevOps  
**Estimated:** 2h

**Description:**
Create comprehensive documentation for the infrastructure

**Deliverables:**

1. **Infrastructure Diagram:**
   ```
   [Client] 
      ↓ HTTPS
   [API Gateway: api-gateway-dev]
      ↓ (okta-authorizer validates JWT)
   [VPC Link]
      ↓
   [Application Load Balancer]
      ↓
   [Target Group: production-support-admin-tg]
      ↓
   [ECS Service: production-support-admin] (2 tasks)
      ↓ HTTPS
   [API Gateway: api-gateway-dev] → [ap-services]
   ```

2. **Resource Inventory:**
   - ECR Repository URL
   - IAM Role ARNs
   - Security Group IDs
   - Target Group ARN
   - ECS Service/Task Definition ARNs
   - API Gateway Route details
   - CloudWatch Log Group
   - Alarm SNS Topic

3. **Environment Variables Document:**
   - List all environment variables
   - Document expected values
   - Mark sensitive values

4. **Deployment Runbook:**
   - How to deploy new image version
   - How to rollback
   - How to scale up/down
   - Common troubleshooting steps

5. **Monitoring Guide:**
   - CloudWatch dashboard links
   - Key metrics to watch
   - Alarm definitions
   - Log query examples

6. **Troubleshooting Guide:**
   - Common issues and solutions
   - How to check ECS task health
   - How to view logs
   - How to test API Gateway integration

**Acceptance:**
- [ ] All documentation created
- [ ] Diagrams are clear and accurate
- [ ] Resource inventory is complete
- [ ] Runbooks are tested
- [ ] Knowledge transfer completed with team

---

## 🔄 Rollback Plan

If critical issues occur after deployment:

1. **Immediate (< 5 min):**
   - Disable API Gateway routes (traffic stops)
   - Or set ECS service desired count to 0

2. **Short-term (< 30 min):**
   - Delete ECS service
   - Keep infrastructure for debugging

3. **Complete Rollback:**
   - Delete all created resources in reverse order
   - ap-services is unaffected (no changes made)

---

## 📊 Dependencies

### External Dependencies
- Existing `api-gateway-dev` and `okta-authorizer` Lambda
- Network infrastructure (VPC, subnets) available
- Application team provides Docker image

### Blockers
- None (using existing infrastructure pattern)

### Related Work
- Application code must handle JWT propagation
- Application must be containerized and pushed to ECR

---

## 🎯 Definition of Done

- [ ] All 11 subtasks completed and verified
- [ ] All acceptance criteria met
- [ ] Infrastructure documented
- [ ] Monitoring and alarms configured
- [ ] Testing completed successfully
- [ ] Team trained on new infrastructure
- [ ] Runbooks created and validated
- [ ] Production deployment approved (if applicable)

---

## 📝 Notes

**Key Differentiator from ap-services:**
- ap-services receives: `Api-User` header only (JWT stripped)
- production-support-admin receives: `Authorization: Bearer <jwt>` + `Api-User` header

**Infrastructure Reuse:**
- ~95% of infrastructure pattern copied from ap-services
- Only VTL template has meaningful change (1 new line for Authorization)
- Minimizes risk and implementation time

**Estimated Timeline:**
- Setup: 1-2 days
- Testing: 0.5-1 day
- Documentation: 0.5 day
- **Total: 2-4 days**

---

## 🔗 References

- ap-services infrastructure (template)
- okta-authorizer Lambda documentation
- API Gateway VTL reference
- ECS Fargate best practices

