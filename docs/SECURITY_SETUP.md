# Security Setup Guide
## JWT-Based Authorization (No Database Required)

**Version:** 1.0  
**Last Updated:** November 6, 2025

---

## Overview

Production Support uses **JWT-based authorization** with role-based access control (RBAC). No database is needed because roles and permissions are embedded in the JWT token from your API Gateway.

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
│  - JWT Validation                          │
│  - Rate Limiting                           │
└──────────────┬─────────────────────────────┘
               │ HTTP (Private VPC)
               │ Bearer Token: JWT with roles
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
│ @PreAuthorize│ │ @PreAuthorize│
└─────────────┘ └─────────────┘
  Private Subnet   Private Subnet
```

**Security Layers:**
1. **API Gateway** - HTTPS termination, initial JWT validation
2. **Private VPC** - Backend not internet-accessible
3. **Backend** - JWT signature validation + role-based authorization
4. **@PreAuthorize** - Method-level permission checks

**Key Benefits:**
- ✅ No database needed
- ✅ Stateless authentication
- ✅ Scales horizontally
- ✅ Roles managed centrally (API Gateway/Auth Service)
- ✅ Defense in depth (multiple security layers)

---

## Supported Roles

### `production_support`
**Permissions:**
- Process queries
- View runbooks
- Execute steps

**Use Case:** Daily operational tasks

### `support_admin`
**Permissions:**
- All `production_support` permissions
- Execute critical operations

**Use Case:** Senior engineers, admin tasks

### Future Roles (Examples)
- `support_viewer` - Read-only access
- `support_manager` - Approval workflows
- `system_admin` - Configuration changes

---

## Configuration

### Development (Security Disabled)

```yaml
# application-dev.yml
spring:
  profiles:
    active: dev
```

**Behavior:**
- All endpoints accessible without authentication
- No JWT validation
- Easier local testing

### Production (Security Enabled)

```yaml
# application-prod.yml
spring:
  profiles:
    active: prod
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: https://your-auth-server.com/.well-known/jwks.json
          issuer-uri: https://your-auth-server.com
```

**Behavior:**
- JWT required for all endpoints (except /health, /swagger-ui)
- Roles extracted from JWT claims
- 403 Forbidden if insufficient permissions

---

## JWT Token Format

### Example JWT Payload

```json
{
  "sub": "engineer@example.com",
  "name": "John Doe",
  "iat": 1699999999,
  "exp": 1700999999,
  "roles": ["ops_engineer"],
  "scope": "production-support"
}
```

### Required Claims

| Claim | Description | Example |
|-------|-------------|---------|
| `sub` | User identifier | `engineer@example.com` |
| `roles` | Array of role names | `["ops_engineer", "support_admin"]` |
| `exp` | Expiration timestamp | `1700999999` |
| `iss` | Token issuer | `https://auth.example.com` |

### Roles Claim Location

Spring Security will look for roles in:
1. `roles` claim
2. `authorities` claim  
3. `scope` claim (if prefixed with `ROLE_`)

**Configure if needed:**
```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
    converter.setAuthoritiesClaimName("roles");
    converter.setAuthorityPrefix("ROLE_");
    
    JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
    jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
    return jwtConverter;
}
```

---

## Controller Authorization

### Method-Level Security

```java
@PreAuthorize("hasAnyRole('ops_engineer', 'support_admin')")
@PostMapping("/process")
public ResponseEntity<OperationalResponse> processRequest(...) {
    // Only accessible to ops_engineer or support_admin
}
```

### Available Expressions

**Role Checks:**
```java
@PreAuthorize("hasRole('ops_engineer')")           // Single role
@PreAuthorize("hasAnyRole('ops_engineer', 'support_admin')")  // Multiple roles
```

**Authority Checks:**
```java
@PreAuthorize("hasAuthority('EXECUTE_STEPS')")     // Single authority
@PreAuthorize("hasAnyAuthority('READ', 'WRITE')") // Multiple authorities
```

**Complex Expressions:**
```java
@PreAuthorize("hasRole('support_admin') and #request.userId == authentication.name")
@PreAuthorize("hasRole('ops_engineer') or hasRole('support_viewer')")
```

**Method Parameters:**
```java
@PreAuthorize("#userId == authentication.name")
public void updateUser(@PathVariable String userId) {
    // Users can only update their own data
}
```

---

## Protected Endpoints

### Secured Endpoints

| Endpoint | Method | Required Roles |
|----------|--------|----------------|
| `/api/v1/process` | POST | `ops_engineer`, `support_admin` |
| `/api/v1/execute-step` | POST | `ops_engineer`, `support_admin` |
| `/api/v1/classify` | POST | `ops_engineer`, `support_admin` |
| `/api/v1/tasks/{taskId}/steps` | GET | Anyone authenticated |

### Public Endpoints (No Auth Required)

- `/api/v1/health` - Health check
- `/actuator/**` - Spring Boot Actuator
- `/swagger-ui/**` - API documentation
- `/api-docs/**` - OpenAPI spec

---

## API Gateway Integration

### AWS API Gateway Setup

**Custom Authorizer:**
```javascript
// Lambda authorizer example
exports.handler = async (event) => {
    const token = event.headers.Authorization.replace('Bearer ', '');
    
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
            roles: JSON.stringify(user.roles),
            userId: user.sub
        }
    };
};
```

**Forward JWT to Backend:**
```yaml
# API Gateway Integration
Integration Request:
  HTTP Headers:
    Authorization: $context.authorizer.token
```

### OAuth2/OIDC Setup

**If using Auth0, Okta, Keycloak:**

```yaml
# application-prod.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Auth0
          issuer-uri: https://your-domain.auth0.com/
          
          # Okta
          issuer-uri: https://your-domain.okta.com/oauth2/default
          
          # Keycloak
          issuer-uri: https://keycloak.example.com/realms/production-support
```

---

## Testing

### Local Testing (Development)

```bash
# Run with dev profile (security disabled)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# All endpoints accessible without token
curl -X POST http://localhost:8093/api/v1/process \
  -H "Content-Type: application/json" \
  -d '{"query": "cancel case 123"}'
```

### Production Testing (With JWT)

```bash
# Get JWT token from auth service
TOKEN=$(curl -X POST https://auth.example.com/oauth/token \
  -d "grant_type=client_credentials" \
  -d "client_id=your-client-id" \
  -d "client_secret=your-secret" | jq -r '.access_token')

# Call API with token
curl -X POST http://localhost:8093/api/v1/process \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query": "cancel case 123"}'
```

### Mock JWT for Testing

**Generate test JWT:**
```bash
# Install jwt-cli
cargo install jwt-cli

# Generate JWT with roles
jwt encode --secret your-secret-key \
  --sub "test@example.com" \
  --exp +1h \
  '{"roles": ["ops_engineer"]}'
```

---

## Error Responses

### 401 Unauthorized

**Missing or invalid token:**
```json
{
  "timestamp": "2025-11-06T00:00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource",
  "path": "/api/v1/process"
}
```

### 403 Forbidden

**Insufficient permissions:**
```json
{
  "timestamp": "2025-11-06T00:00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied",
  "path": "/api/v1/execute-step"
}
```

---

## Security Best Practices

### JWT Validation

✅ **DO:**
- Validate JWT signature
- Check expiration (`exp` claim)
- Verify issuer (`iss` claim)
- Use HTTPS in production
- Rotate signing keys regularly

❌ **DON'T:**
- Store sensitive data in JWT
- Use weak signing algorithms (HS256 with shared secrets)
- Accept unsigned tokens
- Ignore expiration

### Role Management

✅ **DO:**
- Use principle of least privilege
- Assign roles based on job function
- Audit role assignments regularly
- Use descriptive role names

❌ **DON'T:**
- Hard-code roles in application
- Give everyone admin access
- Use generic role names (user, admin)

### Token Handling

✅ **DO:**
- Use short-lived tokens (1-24 hours)
- Implement token refresh
- Store tokens securely (HttpOnly cookies, secure storage)
- Clear tokens on logout

❌ **DON'T:**
- Store tokens in localStorage (XSS risk)
- Share tokens between users
- Log tokens
- Use same token across environments

---

## Troubleshooting

### Issue: 401 Unauthorized

**Check:**
1. JWT is present in `Authorization` header
2. Format is `Bearer <token>`
3. Token hasn't expired
4. Issuer URI is correct

```bash
# Decode JWT to check claims
jwt decode <your-token>
```

### Issue: 403 Forbidden

**Check:**
1. User has required role in JWT
2. Role name matches @PreAuthorize expression
3. Profile is set to 'prod' (dev bypasses security)

```bash
# Check JWT roles claim
jwt decode <your-token> | jq .roles
```

### Issue: Security is bypassed

**Check:**
1. Active profile is 'prod' not 'dev'
2. JWT validation is configured
3. SecurityConfig is loaded

```bash
# Check active profile
curl http://localhost:8093/actuator/env | jq '.propertySources[] | select(.name | contains("applicationConfig"))'
```

---

## Migration from No Auth

If migrating from unauthenticated to authenticated:

**Phase 1: Add security (disabled by default)**
```yaml
spring:
  profiles:
    active: dev  # Security disabled
```

**Phase 2: Test with JWT**
```yaml
spring:
  profiles:
    active: prod  # Security enabled
```

**Phase 3: Roll out gradually**
- Enable for test environment
- Validate with real tokens
- Monitor 401/403 errors
- Enable for production

---

## Summary

✅ **No database needed** - Roles in JWT token  
✅ **@PreAuthorize** - Method-level security  
✅ **Dev mode** - Security disabled for local testing  
✅ **Prod mode** - Full JWT validation  
✅ **API Gateway** - Central authentication  

**Questions?** Contact security team or DevOps.

---

**Last Updated:** November 6, 2025

