# Test JWT Token

## For Dev/Local Profiles

**Use this token in your UI (valid until 2035):**

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJlbmdpbmVlckBleGFtcGxlLmNvbSIsIm5hbWUiOiJUZXN0IEVuZ2luZWVyIiwicm9sZXMiOlsicHJvZHVjdGlvbl9zdXBwb3J0Iiwic3VwcG9ydF9hZG1pbiJdLCJpYXQiOjE3NjI0NjYzNTksImV4cCI6MjA3NzgyNjM1OX0.v8amYkiJOS2dT9MQaZJBkdN-8rWrs-rfxqgVCtgTu3Q
```

## Token Details

**Header:**
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload:**
```json
{
  "sub": "engineer@example.com",
  "name": "Test Engineer",
  "roles": ["production_support", "support_admin"],
  "iat": 1730948000,
  "exp": 2046308000
}
```

**Issued:** November 6, 2024  
**Expires:** November 6, 2035 (10+ years)

## How to Use

### In JavaScript/React UI

```javascript
const TEST_JWT_TOKEN = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJlbmdpbmVlckBleGFtcGxlLmNvbSIsIm5hbWUiOiJUZXN0IEVuZ2luZWVyIiwicm9sZXMiOlsicHJvZHVjdGlvbl9zdXBwb3J0Iiwic3VwcG9ydF9hZG1pbiJdLCJpYXQiOjE3MzA5NDgwMDAsImV4cCI6MjA0NjMwODAwMH0.qKqLfMwGl0v9RYm_4JxQh7YLZCn8f0T6jJ0QHJmZa3Q'

const response = await fetch('http://localhost:8093/api/v1/process', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${TEST_JWT_TOKEN}`
  },
  body: JSON.stringify({
    query: "cancel case 2025123P6732",
    userId: "engineer@example.com"
  })
})
```

### In cURL

```bash
curl -X POST http://localhost:8093/api/v1/process \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJlbmdpbmVlckBleGFtcGxlLmNvbSIsIm5hbWUiOiJUZXN0IEVuZ2luZWVyIiwicm9sZXMiOlsicHJvZHVjdGlvbl9zdXBwb3J0Iiwic3VwcG9ydF9hZG1pbiJdLCJpYXQiOjE3MzA5NDgwMDAsImV4cCI6MjA0NjMwODAwMH0.qKqLfMwGl0v9RYm_4JxQh7YLZCn8f0T6jJ0QHJmZa3Q" \
  -d '{
    "query": "cancel case 2025123P6732",
    "userId": "engineer@example.com"
  }'
```

## Secret Key

**For reference (dev/local only):**
```
test-secret-key-for-development-minimum-256-bits-required-for-hs256-algorithm
```

**⚠️ WARNING:** This is a test secret for local development only. **Never use in production!**

## Generate New Token (If Needed)

Use [jwt.io](https://jwt.io) with:
- Algorithm: HS256
- Secret: `test-secret-key-for-development-minimum-256-bits-required-for-hs256-algorithm`
- Payload: Include `roles` array with `production_support`

Or use jwt-cli:
```bash
npm install -g jsonwebtoken-cli

jwt encode \
  --algorithm HS256 \
  --secret "test-secret-key-for-development-minimum-256-bits-required-for-hs256-algorithm" \
  --exp "10y" \
  '{"sub":"engineer@example.com","name":"Test Engineer","roles":["production_support","support_admin"]}'
```

