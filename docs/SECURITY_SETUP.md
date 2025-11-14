# Security Setup Guide
## API Gateway-Based Authentication & Authorization

**Version:** 2.0  
**Last Updated:** November 14, 2025

---

## Overview

Production Support uses **API Gateway-based authentication and authorization**. The API Gateway handles all authentication, and the backend service receives pre-validated requests with user context in headers.

**Key Points:**
- ✅ **API Gateway handles authentication** - No JWT validation in backend
- ✅ **Headers contain user context** - User ID, roles, etc. passed via headers
- ✅ **Stateless architecture** - No database needed
- ✅ **Simple backend** - Just reads headers, no auth logic

---

## Security Architecture

```
┌────────────────────────────────────────────┐
│  Internet (Public)                         │
└──────────────┬─────────────────────────────┘
               │ HTTPS (TLS 1.2+)
               ▼
┌────────────────────────────────────────────┐
│  API Gateway (xxx.apigtw.com)             │
│  - SSL Termination                         │
│  - Authentication (JWT/OAuth)              │
│  - Authorization (Role-based)              │
│  - Rate Limiting                           │
│  - Request Validation                      │
└──────────────┬─────────────────────────────┘
               │ HTTP (Private VPC)
               │ Headers:
               │ - X-User-ID: user@example.com
               │ - Role-Name: Production Support
               │ - Api-User: user@example.com
               │ - Authorization: Bearer <token>
               ▼
┌────────────────────────────────────────────┐
│  ALB (Internal)                            │
│  - Health Checks                           │
│  - Load Distribution                       │
└──────────────┬─────────────────────────────┘
               │
        ┌──────┴──────┐
        ▼             ▼
┌─────────────┐ ┌─────────────┐
│ ECS Task 1  │ │ ECS Task 2  │
│ HTTP :8093  │ │ HTTP :8093  │
│ (Reads      │ │ (Reads      │
│  Headers)   │ │  Headers)   │
└─────────────┘ └─────────────┘
  Private Subnet   Private Subnet
```

**Security Layers:**
1. **API Gateway** - Handles all authentication and authorization
2. **Private VPC** - Backend not internet-accessible
3. **Backend** - Simply reads headers, no auth logic needed
4. **Header-based context** - User info passed via standard headers

**Key Benefits:**
- ✅ No authentication logic in backend
- ✅ Centralized security at API Gateway
- ✅ Stateless backend (scales horizontally)
- ✅ Simple implementation (just read headers)

---

## Headers Passed by API Gateway

### Standard Headers

The API Gateway forwards the following headers to the backend:

| Header | Description | Example |
|--------|-------------|---------|
| `X-User-ID` | User identifier | `engineer@example.com` |
| `Role-Name` | User's role | `Production Support` |
| `Api-User` | API user identifier | `engineer@example.com` |
| `Authorization` | Bearer token (if needed) | `Bearer eyJhbGc...` |
| `Lab-Id` | Lab identifier (if applicable) | `lab-123` |
| `Discipline-Name` | Discipline (if applicable) | `Pathology` |
| `Time-Zone` | User timezone | `America/New_York` |
| `accept` | Accept header | `application/json` |

### Header Usage in Runbooks

Runbooks can check headers using `HEADER_CHECK` step type:

```yaml
- stepNumber: 1
  stepType: "prechecks"
  name: "Verify User Has Permission"
  method: "HEADER_CHECK"
  path: "Role-Name"
  expectedResponse: "Production Support"
  autoExecutable: true
```

---

## Supported Roles

### `Production Support`
**Permissions:**
- Process queries
- View runbooks
- Execute steps

**Use Case:** Daily operational tasks

### `Support Admin`
**Permissions:**
- All `Production Support` permissions
- Execute critical operations

**Use Case:** Senior engineers, admin tasks

### Future Roles (Examples)
- `Support Viewer` - Read-only access
- `Support Manager` - Approval workflows
- `System Admin` - Configuration changes

---

## Configuration

### Development (No API Gateway)

```yaml
# application-dev.yml
spring:
  profiles:
    active: dev
```

**Behavior:**
- All endpoints accessible without authentication
- Headers can be manually set for testing
- Easier local development

### Production (With API Gateway)

```yaml
# application-prod.yml
spring:
  profiles:
    active: prod
```

**Behavior:**
- API Gateway handles all authentication
- Backend receives pre-validated requests
- Headers contain user context
- No additional security configuration needed

---

## API Gateway Setup

### AWS API Gateway Example

**Authorizer Configuration:**
```javascript
// Lambda authorizer
exports.handler = async (event) => {
    const token = event.headers.Authorization?.replace('Bearer ', '');
    
    // Validate JWT with your auth service
    const user = await validateJWT(token);
    
    return {
        principalId: user.sub,
        policyDocument: {
            Version: '2012-10-17',
            Statement: [{
                Action: 'execute-api:Invoke',
                Effect: 'Allow',
                Resource: event.methodArn
            }]
        },
        context: {
            userId: user.sub,
            roleName: user.roles?.[0] || 'default',
            apiUser: user.sub
        }
    };
};
```

**Integration Request:**
```yaml
# Forward headers to backend
Integration Request:
  HTTP Headers:
    X-User-ID: $context.authorizer.userId
    Role-Name: $context.authorizer.roleName
    Api-User: $context.authorizer.apiUser
    Authorization: $input.params('Authorization')
```

### Other API Gateways

**Kong:**
```yaml
plugins:
  - name: jwt
    config:
      secret_is_base64: false
  - name: request-transformer
    config:
      add:
        headers:
          - X-User-ID:$(consumer.username)
          - Role-Name:$(consumer.role)
```

**NGINX:**
```nginx
location /api/ {
    proxy_set_header X-User-ID $http_x_user_id;
    proxy_set_header Role-Name $http_role_name;
    proxy_pass http://backend:8093;
}
```

---

## Testing

### Local Testing (Development)

```bash
# Run with dev profile (no auth required)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Test with manual headers
curl -X POST http://localhost:8093/api/v1/process \
  -H "Content-Type: application/json" \
  -H "X-User-ID: test@example.com" \
  -H "Role-Name: Production Support" \
  -d '{"query": "cancel case 123"}'
```

### Production Testing (With API Gateway)

```bash
# Get token from auth service
TOKEN=$(curl -X POST https://auth.example.com/oauth/token \
  -d "grant_type=client_credentials" \
  -d "client_id=your-client-id" \
  -d "client_secret=your-secret" | jq -r '.access_token')

# Call via API Gateway
curl -X POST https://api.example.com/api/v1/process \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query": "cancel case 123"}'
```

---

## Header Check Examples

### Verify User Role

```yaml
- stepNumber: 1
  stepType: "prechecks"
  name: "Check if user has Production Support role"
  method: "HEADER_CHECK"
  path: "Role-Name"
  expectedResponse: "Production Support"
  autoExecutable: true
```

### Verify User ID

```yaml
- stepNumber: 1
  stepType: "prechecks"
  name: "Verify User ID"
  method: "HEADER_CHECK"
  path: "X-User-ID"
  expectedResponse: "{user_id}"  # Placeholder from request
  autoExecutable: true
```

---

## Error Responses

### 401 Unauthorized (From API Gateway)

**Missing or invalid token:**
```json
{
  "message": "Unauthorized",
  "statusCode": 401
}
```

### 403 Forbidden (From API Gateway)

**Insufficient permissions:**
```json
{
  "message": "Forbidden",
  "statusCode": 403
}
```

### 400 Bad Request (From Backend)

**Missing required header:**
```json
{
  "timestamp": "2025-11-14T00:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Missing required header: X-User-ID",
  "path": "/api/v1/process"
}
```

---

## Security Best Practices

### API Gateway

✅ **DO:**
- Validate JWT signature
- Check expiration (`exp` claim)
- Verify issuer (`iss` claim)
- Use HTTPS in production
- Rate limit requests
- Log security events

❌ **DON'T:**
- Bypass authentication
- Forward invalid tokens
- Expose backend directly
- Skip rate limiting

### Backend

✅ **DO:**
- Trust headers from API Gateway
- Validate required headers exist
- Log header values for audit
- Use header values for authorization checks

❌ **DON'T:**
- Validate JWT tokens (API Gateway does this)
- Implement authentication logic
- Trust headers from untrusted sources
- Expose endpoints without API Gateway

---

## Troubleshooting

### Issue: Missing Headers

**Check:**
1. API Gateway is forwarding headers correctly
2. Integration request includes header mapping
3. Headers are not being stripped by ALB/proxy

```bash
# Check what headers backend receives
curl -v http://localhost:8093/api/v1/process \
  -H "X-User-ID: test@example.com"
```

### Issue: Wrong Role

**Check:**
1. API Gateway authorizer returns correct role
2. Header name matches runbook expectation
3. Role value matches `expectedResponse` in YAML

### Issue: 401/403 from API Gateway

**Check:**
1. Token is valid and not expired
2. Token has required scopes/roles
3. API Gateway authorizer is configured correctly

---

## Migration Notes

### From JWT Validation to Header-Based

If migrating from JWT validation in backend to API Gateway:

**Before:**
```java
@PreAuthorize("hasRole('ops_engineer')")
public ResponseEntity<OperationalResponse> processRequest(...) {
    // JWT validated by Spring Security
}
```

**After:**
```java
// No @PreAuthorize needed - API Gateway handles auth
public ResponseEntity<OperationalResponse> processRequest(...) {
    String userId = request.getHeader("X-User-ID");
    String role = request.getHeader("Role-Name");
    // Use headers directly
}
```

---

## Summary

✅ **API Gateway handles authentication** - No backend auth logic  
✅ **Headers contain user context** - Simple header reading  
✅ **Stateless backend** - Scales horizontally  
✅ **No database needed** - All context in headers  
✅ **Simple implementation** - Just read headers from requests  

**Questions?** Contact security team or DevOps.

---

**Last Updated:** November 14, 2025
