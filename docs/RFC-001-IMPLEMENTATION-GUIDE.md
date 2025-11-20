# RFC-001 Implementation Guide: Operational Data API

**Companion Document to:** [RFC-001: Operational Data API](./RFC-001-OPERATIONAL-DATA-API.md)  
**Audience:** Engineers, DevOps, Database Admins  
**Purpose:** Technical implementation details, code examples, configurations

---

## Table of Contents
1. [Service Configuration](#service-configuration)
2. [Database Connection Setup](#database-connection-setup)
3. [API Implementation Examples](#api-implementation-examples)
4. [YAML Runbook Configuration](#yaml-runbook-configuration)
5. [Monitoring & Alerting](#monitoring--alerting)
6. [Testing Examples](#testing-examples)
7. [Deployment Configuration](#deployment-configuration)

---

## Service Configuration

### production-support-admin WebClient Configuration

```yaml
# src/main/resources/application.yml
downstream:
  services:
    lims-histology-data-api:
      baseUrl: https://api-gateway.example.com/lims-histology-data-api
      timeout: 30
    ap-services:
      baseUrl: https://api-gateway.example.com/ap-services
      timeout: 30
```

### <lims-histology-data-api> Application Configuration

```yaml
# application.yml
server:
  port: 8080
  shutdown: graceful

spring:
  application:
    name: lims-histology-data-api
    
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 5000
      idle-timeout: 300000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
      pool-name: LimsOperationsPool
      connection-test-query: SELECT 1

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          batch_size: 20

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      cloudwatch:
        enabled: true
```

---

## Database Connection Setup

### Create Database User

```sql
-- Create dedicated user for operational service
CREATE USER lims_operations_user WITH PASSWORD 'secure_password';

-- Grant necessary permissions
GRANT SELECT, INSERT, UPDATE ON cases TO lims_operations_user;
GRANT SELECT, INSERT ON case_audit_log TO lims_operations_user;
GRANT SELECT ON samples TO lims_operations_user;
GRANT SELECT ON materials TO lims_operations_user;
GRANT USAGE ON SEQUENCE case_audit_log_id_seq TO lims_operations_user;

-- Set connection limit
ALTER USER lims_operations_user CONNECTION LIMIT 12;

-- Set statement timeout
ALTER USER lims_operations_user SET statement_timeout = '10s';

-- Set idle timeout
ALTER USER lims_operations_user SET idle_in_transaction_session_timeout = '60s';
```

### Connection Pool Configuration

```java
@Configuration
public class DatabaseConfig {
    
    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        
        // Pool sizing
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        
        // Timeouts
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1800000);
        
        // Leak detection
        config.setLeakDetectionThreshold(60000);
        
        // Monitoring
        config.setPoolName("LimsOperationsPool");
        config.setRegisterMbeans(true);
        
        // Connection test
        config.setConnectionTestQuery("SELECT 1");
        
        return new HikariDataSource(config);
    }
}
```

### Database Indexes

```sql
-- Ensure indexes exist for operational queries
CREATE INDEX CONCURRENTLY idx_cases_status ON cases(status);
CREATE INDEX CONCURRENTLY idx_cases_updated_at ON cases(updated_at) WHERE updated_at IS NOT NULL;
CREATE INDEX CONCURRENTLY idx_case_audit_log_case_id ON case_audit_log(case_id);
CREATE INDEX CONCURRENTLY idx_case_audit_log_action_time ON case_audit_log(action, performed_at);
```

---

## API Implementation Examples

### Cancel Case Endpoint

```java
@RestController
@RequestMapping("/operations/case")
@RequiredArgsConstructor
@Slf4j
public class CaseOperationsController {
    
    private final CaseOperationsService caseOperationsService;
    
    @PatchMapping("/{caseId}/cancel")
    public ResponseEntity<OperationResult> cancelCase(
            @PathVariable String caseId,
            @RequestBody CancelRequest request,
            @RequestHeader("X-User-ID") String userId,
            @RequestHeader(value = "Api-User", required = false) String apiUser,
            @RequestHeader(value = "Lab-Id", required = false) String labId) {
        
        log.info("Cancelling case {} by user {}", caseId, userId);
        
        OperationResult result = caseOperationsService.cancelCase(
            caseId, request, userId, apiUser, labId);
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{caseId}/status")
    public ResponseEntity<CaseStatusResponse> getCaseStatus(
            @PathVariable String caseId) {
        
        CaseStatusResponse status = caseOperationsService.getCaseStatus(caseId);
        return ResponseEntity.ok(status);
    }
}
```

### Service Implementation

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CaseOperationsService {
    
    private final CaseRepository caseRepository;
    private final AuditLogRepository auditLogRepository;
    
    @Transactional(timeout = 10)
    public OperationResult cancelCase(
            String caseId, 
            CancelRequest request, 
            String userId,
            String apiUser,
            String labId) {
        
        // Validate case exists
        Case caseObj = caseRepository.findById(caseId)
            .orElseThrow(() -> new CaseNotFoundException(caseId));
        
        // Validate state transition
        if (caseObj.getStatus() == CaseStatus.CANCELLED) {
            throw new InvalidStateException("Case already cancelled");
        }
        
        // Update case
        caseObj.setStatus(CaseStatus.CANCELLED);
        caseObj.setCancelledAt(Instant.now());
        caseObj.setCancelledBy(userId);
        caseObj.setCancellationReason(request.getReason());
        caseObj.setUpdatedAt(Instant.now());
        caseRepository.save(caseObj);
        
        // Audit log
        AuditLog audit = AuditLog.builder()
            .caseId(caseId)
            .action("CANCELLED")
            .performedBy(userId)
            .performedAt(Instant.now())
            .details(Map.of(
                "reason", request.getReason(),
                "notes", request.getNotes(),
                "apiUser", apiUser,
                "labId", labId
            ))
            .build();
        auditLogRepository.save(audit);
        
        // TODO: Trigger notifications if needed
        
        log.info("Successfully cancelled case {}", caseId);
        
        return OperationResult.builder()
            .success(true)
            .caseId(caseId)
            .status("cancelled")
            .timestamp(Instant.now())
            .build();
    }
    
    @Transactional(readOnly = true, timeout = 5)
    public CaseStatusResponse getCaseStatus(String caseId) {
        Case caseObj = caseRepository.findById(caseId)
            .orElseThrow(() -> new CaseNotFoundException(caseId));
        
        return CaseStatusResponse.builder()
            .caseId(caseId)
            .status(caseObj.getStatus().name())
            .updatedAt(caseObj.getUpdatedAt())
            .cancelledAt(caseObj.getCancelledAt())
            .build();
    }
}
```

### Repository

```java
@Repository
public interface CaseRepository extends JpaRepository<Case, String> {
    
    @Query("SELECT c FROM Case c WHERE c.id = :caseId")
    Optional<Case> findById(@Param("caseId") String caseId);
    
    @Modifying
    @Query("UPDATE Case c SET c.status = :status, c.updatedAt = :updatedAt " +
           "WHERE c.id = :caseId")
    int updateStatus(
        @Param("caseId") String caseId,
        @Param("status") CaseStatus status,
        @Param("updatedAt") Instant updatedAt);
}
```

---

## YAML Runbook Configuration

### Updated cancel-case.yaml

```yaml
useCase:
  id: "CANCEL_CASE"
  name: "Cancel Case"
  description: "Complete cancellation of a case including cleanup of associated workflows"
  category: "case-management"
  version: "3.1"
  downstreamService: "lims-histology-data-api"  # <-- Routes to new service

classification:
  keywords:
    - "cancel case"
    - "delete case"
    - "abort case"
  requiredEntities:
    - case_id

extraction:
  entities:
    case_id:
      type: "string"
      patterns:
        - "(\\d{7}[A-Z]\\d{4})"
      required: true
      validation:
        regex: "^\\d{4,}\\w*$"
        errorMessage: "Case ID must be in format: 2025123P6732"

execution:
  timeout: 30
  retryPolicy:
    maxAttempts: 3
    backoffMs: 1000
  
  steps:
    - stepNumber: 1
      stepType: "prechecks"
      name: "Verify User Has Cancel Permission"
      method: "HEADER_CHECK"
      path: "Role-Name"
      expectedResponse: "Production Support"
      autoExecutable: true
    
    - stepNumber: 2
      stepType: "prechecks"
      name: "Preview Cancellation Impact"
      method: "LOCAL_MESSAGE"
      localMessage: "Case will be cancelled and removed from workpool"
      autoExecutable: true
    
    - stepNumber: 3
      stepType: "procedure"
      name: "Execute Case Cancellation"
      method: "PATCH"
      path: "/operations/case/{case_id}/cancel"
      headers:
        Api-User: "{api_user}"
        Lab-Id: "{lab_id}"
        Content-Type: "application/json"
      body:
        reason: "operational_request"
        notes: "Cancelled via Production Support"
        notify_stakeholders: true
      expectedStatus: 200
      autoExecutable: false
    
    - stepNumber: 4
      stepType: "postchecks"
      name: "Verify Case Status"
      method: "GET"
      path: "/operations/case/{case_id}/status"
      expectedStatus: 200
      expectedResponse: "cancelled"
      autoExecutable: true
```

---

## Monitoring & Alerting

### CloudWatch Dashboard Configuration

```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "title": "Database Connections by Service",
        "metrics": [
          ["HikariCP", "pool.connections.active", "service", "lims-histology-data-api"],
          ["HikariCP", "pool.connections.max", "service", "lims-histology-data-api"]
        ]
      }
    },
    {
      "type": "metric",
      "properties": {
        "title": "Connection Pool Utilization %",
        "metrics": [
          ["HikariCP", "pool.connections.utilization", "service", "lims-histology-data-api"]
        ],
        "yAxis": {
          "left": { "min": 0, "max": 100 }
        }
      }
    },
    {
      "type": "metric",
      "properties": {
        "title": "Operation Success Rate",
        "metrics": [
          ["Operations", "success.rate", "service", "lims-histology-data-api"]
        ]
      }
    }
  ]
}
```

### Alert Rules

```yaml
# CloudWatch Alarms
alerts:
  - name: ConnectionPoolNearLimit
    metric: hikari.connections.active
    threshold: 8
    severity: warning
    
  - name: ConnectionPoolExhausted
    metric: hikari.connections.pending
    threshold: 0
    comparator: greater_than
    severity: critical
    
  - name: OperationFailureRate
    metric: operations.failure.rate
    threshold: 5
    unit: percent
    severity: critical
    
  - name: SlowQueries
    metric: query.duration.p95
    threshold: 1000
    unit: milliseconds
    severity: warning
```

---

## Testing Examples

### Load Test Script

```python
# scripts/load-test.py
import requests
import concurrent.futures
import time

def execute_cancel(case_id):
    response = requests.patch(
        f'https://api.example.com/operations/case/{case_id}/cancel',
        json={'reason': 'test', 'notes': 'load test'},
        headers={
            'Authorization': 'Bearer TOKEN',
            'X-User-ID': 'load-test',
            'Api-User': 'test-user',
            'Lab-Id': 'test-lab'
        }
    )
    return response.status_code, response.elapsed.total_seconds()

def load_test(concurrency, num_requests):
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [
            executor.submit(execute_cancel, f"CASE-{i}")
            for i in range(num_requests)
        ]
        results = [f.result() for f in concurrent.futures.as_completed(futures)]
    
    success = sum(1 for status, _ in results if status == 200)
    print(f"Success: {success}/{num_requests} ({success/num_requests*100:.1f}%)")

# Test scenarios
load_test(concurrency=5, num_requests=50)
load_test(concurrency=15, num_requests=100)
```

### Contract Tests

```java
@SpringBootTest
class CancelCaseContractTest {
    
    @Test
    void cancelCase_validRequest_returns200() {
        CancelRequest request = new CancelRequest(
            "operational_request",
            "Test cancellation",
            true
        );
        
        ResponseEntity<OperationResult> response = 
            controller.cancelCase("2025123P6732", request, "user123", "api-user", "lab-123");
        
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("cancelled", response.getBody().getStatus());
    }
    
    @Test
    void cancelCase_alreadyCancelled_returns409() {
        // Test idempotency and error handling
    }
}
```

---

## Deployment Configuration

### ECS Task Definition

```json
{
  "family": "lims-histology-data-api",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "lims-histology-data-api",
      "image": "ACCOUNT.dkr.ecr.REGION.amazonaws.com/lims-histology-data-api:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "prod"
        },
        {
          "name": "JAVA_OPTS",
          "value": "-Xms256m -Xmx768m -XX:+UseG1GC"
        }
      ],
      "secrets": [
        {
          "name": "DATABASE_URL",
          "valueFrom": "arn:aws:secretsmanager:REGION:ACCOUNT:secret:prod/lims/db-url"
        },
        {
          "name": "DATABASE_USER",
          "valueFrom": "arn:aws:secretsmanager:REGION:ACCOUNT:secret:prod/lims-ops/db-user"
        },
        {
          "name": "DATABASE_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:REGION:ACCOUNT:secret:prod/lims-ops/db-password"
        }
      ],
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      },
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/lims-histology-data-api",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

### Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY target/lims-histology-data-api-*.jar app.jar

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

ENV JAVA_OPTS="-Xms256m -Xmx768m -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## Database Query Examples

### Verify Connection Health

```sql
-- Check active connections for operational service
SELECT 
    datname,
    usename,
    count(*) as connection_count,
    state
FROM pg_stat_activity
WHERE usename = 'lims_operations_user'
GROUP BY datname, usename, state;

-- Check connection pool status
SELECT 
    usename,
    count(*) as current_connections,
    (SELECT rolconnlimit FROM pg_roles WHERE rolname = usename) as connection_limit
FROM pg_stat_activity
WHERE usename = 'lims_operations_user'
GROUP BY usename;
```

### Find Slow Queries

```sql
-- Find slow queries from operational service
SELECT 
    substring(query, 1, 100) AS query_snippet,
    mean_exec_time,
    calls,
    total_exec_time
FROM pg_stat_statements
WHERE userid = (SELECT oid FROM pg_roles WHERE rolname = 'lims_operations_user')
ORDER BY mean_exec_time DESC
LIMIT 10;
```

---

## Health Check Implementation

```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    
    @Override
    public Health health() {
        try {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            HikariPoolMXBean poolProxy = hikariDataSource.getHikariPoolMXBean();
            
            int activeConnections = poolProxy.getActiveConnections();
            int totalConnections = poolProxy.getTotalConnections();
            int idleConnections = poolProxy.getIdleConnections();
            int maxPoolSize = hikariDataSource.getMaximumPoolSize();
            
            Map<String, Object> details = Map.of(
                "active", activeConnections,
                "idle", idleConnections,
                "total", totalConnections,
                "max", maxPoolSize,
                "utilization_percent", (activeConnections * 100 / maxPoolSize)
            );
            
            if (activeConnections > maxPoolSize * 0.8) {
                return Health.up()
                    .withDetails(details)
                    .withDetail("warning", "Connection pool utilization high")
                    .build();
            }
            
            return Health.up().withDetails(details).build();
            
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
```

---

## Circuit Breaker Configuration

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      database:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
```

```java
@Service
public class CaseOperationsService {
    
    @CircuitBreaker(name = "database", fallbackMethod = "databaseUnavailableFallback")
    @Transactional(timeout = 10)
    public OperationResult cancelCase(String caseId, CancelRequest request, String userId) {
        // Implementation
    }
    
    private OperationResult databaseUnavailableFallback(String caseId, CancelRequest request, 
                                                        String userId, Exception e) {
        log.error("Database unavailable for case {}", caseId, e);
        throw new ServiceUnavailableException(
            "Database temporarily unavailable. Please try again in 30 seconds.");
    }
}
```

---

## API Contract

### OpenAPI Specification

```yaml
openapi: 3.0.0
info:
  title: LIMS Operations API
  version: 1.0.0
  description: Operational data API for production support autonomy

paths:
  /operations/case/{caseId}/cancel:
    patch:
      summary: Cancel a case
      parameters:
        - name: caseId
          in: path
          required: true
          schema:
            type: string
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [reason]
              properties:
                reason:
                  type: string
                  enum: [operational_request, data_quality, duplicate, other]
                notes:
                  type: string
                notify_stakeholders:
                  type: boolean
      responses:
        '200':
          description: Success
        '404':
          description: Case not found
        '409':
          description: Case already cancelled
          
  /operations/case/{caseId}/status:
    get:
      summary: Get case status
      parameters:
        - name: caseId
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: Success
          content:
            application/json:
              schema:
                type: object
                properties:
                  case_id:
                    type: string
                  status:
                    type: string
                  updated_at:
                    type: string
                    format: date-time
```

---

## Incident Response

### Connection Pool Exhausted

```bash
# Diagnose
kubectl exec -it lims-histology-data-api-pod -- curl localhost:8080/actuator/health

# Check database connections
psql -h DB_HOST -U admin -c "
  SELECT pid, state, query_start, query 
  FROM pg_stat_activity 
  WHERE usename = 'lims_operations_user';"

# Emergency: Kill idle connections
psql -h DB_HOST -U admin -c "
  SELECT pg_terminate_backend(pid)
  FROM pg_stat_activity
  WHERE usename = 'lims_operations_user'
    AND state = 'idle'
    AND state_change < now() - interval '5 minutes';"

# Restart service
kubectl rollout restart deployment/lims-histology-data-api
```

---

## Performance Tuning

### Query Optimization

```java
// ✅ GOOD: Fetch only needed columns
@Query("SELECT c.id, c.status, c.updatedAt FROM Case c WHERE c.id = :caseId")
CaseStatusProjection getCaseStatus(@Param("caseId") String caseId);

// ❌ BAD: Fetch entire entity
Case findById(String caseId);
```

### Batch Operations

```java
@Transactional(timeout = 30)
public List<OperationResult> cancelCases(List<String> caseIds) {
    // Batch fetch
    List<Case> cases = caseRepository.findAllById(caseIds);
    
    // Batch update
    cases.forEach(c -> {
        c.setStatus(CaseStatus.CANCELLED);
        c.setCancelledAt(Instant.now());
    });
    
    caseRepository.saveAll(cases);
    
    return cases.stream()
        .map(c -> OperationResult.success(c.getId()))
        .collect(Collectors.toList());
}
```

---

## Migration Script

### Flyway Migration

```sql
-- V1__create_operational_indexes.sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cases_status 
ON cases(status);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_cases_updated_at 
ON cases(updated_at) WHERE updated_at IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_case_audit_log_case_id 
ON case_audit_log(case_id);

-- Add operational metadata
ALTER TABLE cases 
ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(100),
ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;
```

---

## Reference Implementation Checklist

### Service Setup
- [ ] Spring Boot 3.2 project created
- [ ] Database connection configured (10 connection limit)
- [ ] HikariCP pool configured
- [ ] Health checks implemented
- [ ] Logging configured

### Database
- [ ] User created with connection limit
- [ ] Permissions granted (least privilege)
- [ ] Indexes created
- [ ] Migration scripts ready

### API Endpoints
- [ ] POST /operations/case/{id}/cancel
- [ ] GET /operations/case/{id}/status
- [ ] Error handling implemented
- [ ] OpenAPI documentation

### Monitoring
- [ ] CloudWatch metrics enabled
- [ ] Custom metrics for operations
- [ ] Alerts configured
- [ ] Dashboard created

### Testing
- [ ] Unit tests (>90% coverage)
- [ ] Integration tests
- [ ] Contract tests
- [ ] Load tests

### Deployment
- [ ] Dockerfile created
- [ ] ECS task definition
- [ ] CI/CD pipeline
- [ ] Secrets management

---

**See Also:**
- [RFC-001: Operational Data API](./RFC-001-OPERATIONAL-DATA-API.md) - Architecture decision
- [RUNBOOK_GUIDELINES.md](./RUNBOOK_GUIDELINES.md) - YAML runbook structure
- [ARCHITECTURE.md](./ARCHITECTURE.md) - Overall system architecture

