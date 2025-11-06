# UI Integration Guide
## Production Support Frontend Integration

**Version:** 1.0  
**Date:** November 6, 2025  
**Status:** Ready for Implementation

---

## Overview

This guide describes how to integrate a frontend UI with the Production Support backend API. Engineers can use this as a requirements specification to build their own implementation.


---

## Table of Contents
1. [Architecture](#architecture)
2. [API Integration](#api-integration)
3. [Data Models](#data-models)
4. [Core Features](#core-features)
5. [User Flow](#user-flow)
6. [UI Components](#ui-components)
7. [Error Handling](#error-handling)
8. [Authentication](#authentication)
9. [Best Practices](#best-practices)

---

## 1. Architecture

### Framework Agnostic

This guide is **framework-agnostic**. Engineers can choose any UI framework they like

**Focus on:**
- API contracts and data flow
- Business logic and requirements
- User experience expectations

---

## 2. API Integration

### Base URL
```typescript
const API_BASE_URL = 'http://localhost:8093/api/v1'
// Production: https://xxx.apigtw.com/production-support/api/v1
```

### Endpoints

#### 2.1 Process Query
**POST** `/api/v1/process`

**Request:**
```typescript
interface ProcessRequest {
  query: string           // Natural language query
  userId: string          // User identifier
  taskId?: string         // Optional: override classification
}
```

**Response:**
```typescript
interface ProcessResponse {
  taskId: string          // e.g., "CANCEL_CASE"
  taskName: string        // e.g., "Cancel Case"
  extractedEntities: {
    case_id?: string
    status?: string
    [key: string]: string | undefined
  }
  steps: Step[]
  warnings?: string[]
}
```

#### 2.2 Execute Step
**POST** `/api/v1/execute-step`

**Request:**
```typescript
interface ExecuteStepRequest {
  taskId: string
  stepNumber: number
  entities: Record<string, string>
  userId: string
  authToken: string
}
```

**Response:**
```typescript
interface ExecuteStepResponse {
  success: boolean
  stepNumber: number
  message: string
  apiResponse?: {
    status: number
    body: string
  }
  executionTimeMs: number
  nextStep?: number
  error?: string
}
```

---

## 3. Data Models

### Data Structures

**Note:** Examples shown in TypeScript for clarity, but use any language/type system.

```typescript
// Step in runbook
interface Step {
  stepNumber: number
  description: string
  method: string           // GET, POST, PATCH, etc.
  path: string            // API endpoint
  requestBody?: string
  expectedResponse?: string
  autoExecutable: boolean // Can be auto-executed?
  stepType: string        // precheck, procedure, postcheck, rollback
}

// Classification response from backend
interface ClassificationResponse {
  taskId: string
  taskName: string
  extractedEntities: Record<string, string>
  steps: Step[]
  warnings?: string[]
}

// Step execution tracking
interface StepExecution {
  stepId: string
  requestId: string
  stepName: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'APPROVAL_REQUIRED'
  type: 'VALIDATION' | 'PERMISSION_CHECK' | 'API_EXECUTION' | 'VERIFICATION'
  requiresApproval: boolean
  startedAt?: string
  completedAt?: string
  result?: {
    success: boolean
    message: string
    data?: Record<string, unknown>
    statusCode?: number
  }
  errorMessage?: string
}
```

---

## 4. Core Features

### 4.1 Natural Language Input

**Requirements:**
- Multi-line textarea that auto-expands
- Submit on Enter (Shift+Enter for new line)
- Disable input while processing
- Clear input after successful submission
- Show placeholder: "💬 How can I help you today?"

**Example Queries:**
```
"please cancel case 2025123P6732"
"update case status to pending 2024123P6731"
"Can you mark status to grossing for caseid 2025123P6732"
```

### 4.2 Classification Display

**Show:**
- Task ID and Task Name
- Extracted entities (case_id, status, etc.)
- Warnings (if any)
- Processing time indicator

**Layout:** Horizontal cards for compact display

### 4.3 Step Execution

**Features:**
- Display all steps from runbook
- Color-code by type: precheck, procedure, postcheck, rollback
- Show step status: pending, running, completed, failed
- Auto-execute eligible steps
- Manual approval for critical steps
- Real-time status updates

**Step States:**
- ○ **Pending** - Not started
- ⟳ **Running** - In progress
- ✓ **Completed** - Success
- ✗ **Failed** - Error occurred
- ⏸ **Approval Required** - Waiting for user

### 4.4 Auto-Execution Logic

```typescript
// Auto-execute ONLY if:
1. Step is marked as autoExecutable: true
2. No non-auto-executable steps come before it
3. Previous step (if any) completed successfully

// Example:
Step 1: autoExecutable=true  → Auto-execute ✓
Step 2: autoExecutable=true  → Auto-execute ✓
Step 3: autoExecutable=false → Require approval ⏸
Step 4: autoExecutable=true  → Do NOT auto-execute (step 3 needs approval)
```

### 4.5 Manual Execution

**For non-auto-executable steps:**
- Show "Approve & Run" button
- On click:
  - Show confirmation (optional)
  - Execute step with `skipApproval: true`
  - Update UI with result

---

## 5. User Flow

### Happy Path

```
1. User enters query
   ↓
2. Click Send or press Enter
   ↓
3. Loading indicator shows
   ↓
4. Backend processes query
   ↓
5. UI displays classification + steps
   ↓
6. Auto-execute first eligible step
   ↓
7. Show step result
   ↓
8. Auto-execute next step (if eligible)
   ↓
9. Continue until manual approval needed or complete
   ↓
10. User manually approves critical steps
    ↓
11. All steps complete → Show success
```

### Error Path

```
1. Query fails validation
   → Show validation error message
   
2. Classification returns UNKNOWN
   → Show "Could not understand query" message
   
3. Step execution fails
   → Show error in step card
   → Stop auto-execution chain
   → Allow manual retry
```

---

## 6. UI Components

### 6.1 Greeting Section

**Display time-based greeting:**
```typescript
const getGreeting = (): string => {
  const hour = new Date().getHours()
  if (hour >= 5 && hour < 12) return 'Good morning'
  if (hour >= 12 && hour < 17) return 'Good afternoon'
  if (hour >= 17 && hour < 21) return 'Good evening'
  return 'Good night'
}
```

**Icon:** 🌐 or similar

### 6.2 Input Container

**Elements:**
- Textarea (auto-expanding)
- Attach button (for future file upload)
- Send button (arrow icon)

**States:**
- Normal: White background
- Focused: Highlight border
- Disabled: Gray out when loading

### 6.3 Loading Indicator

**Show when:**
- Submitting query
- Waiting for classification
- Executing steps

**Display:**
- Spinner animation
- Text: "⚡ Processing your request..."

### 6.4 Response Card

**Sections:**
1. **Classification**
   - Task ID
   - Task Name

2. **Extracted Entities**
   - Key-value pairs
   - Only show if present

3. **Warnings**
   - Show with ⚠️ icon
   - Orange/yellow color

### 6.5 Step Card

**Layout:**
```
┌─────────────────────────────────────────────────┐
│ [#] Step Description              [Status Icon] │
│ GET /api/cases/{case_id}          [Precheck]   │
│                                    [Run Button] │
└─────────────────────────────────────────────────┘
  └─ Result: ✓ Case exists (200 OK)
```

**Elements:**
- Step number badge
- Description text
- HTTP method + path
- Step type badge (precheck/procedure/postcheck/rollback)
- Status indicator
- Action button (Run / Approve & Run)
- Result message (if executed)

**Color Coding:**
- **Precheck:** Blue
- **Procedure:** Purple
- **Postcheck:** Green
- **Rollback:** Red

### 6.6 Error Display

**Format:**
```
⚠️ Error: [Error message]
```

**Types:**
- Network errors
- Validation errors
- API errors
- Step execution failures

---

## 7. Error Handling

### Client-Side Validation

```typescript
// Before sending request
if (!query.trim()) {
  return // Don't submit empty queries
}

// Validate response
if (!response.ok) {
  throw new Error(`API error: ${response.status}`)
}
```

### Backend Error Responses

**Validation Error (400):**
```json
{
  "timestamp": "2025-11-06T00:00:00",
  "status": 400,
  "error": "Validation Failed",
  "message": "Invalid request parameters",
  "errors": {
    "query": "Query is required and cannot be empty"
  }
}
```

**Handle and display to user:**
```typescript
if (response.status === 400) {
  const error = await response.json()
  showError(error.errors.query || error.message)
}
```

### Network Errors

```typescript
try {
  const response = await fetch(...)
} catch (err) {
  if (err instanceof TypeError) {
    showError("Network error: Cannot reach server")
  } else {
    showError(err.message)
  }
}
```

---

## 8. Authentication

### Headers Required

```typescript
const headers = {
  'Content-Type': 'application/json',
  'X-User-ID': userId,
  'X-Idempotency-Key': crypto.randomUUID(), // Prevent duplicate requests
  'Authorization': `Bearer ${jwtToken}`
}
```

### JWT Token

**Structure:**
```json
{
  "sub": "ops-engineer-test",
  "name": "Test Operator",
  "iat": 1699999999,
  "exp": 1700999999,
  "roles": ["ops_engineer"]
}
```

**Obtain from:**
- OAuth2 flow (production)
- Environment variable (development)
- Local storage / session storage

### CORS Configuration

**Backend is configured to allow:**
- `http://localhost:5173` (Vite default)
- `http://localhost:3000` (React/Next.js)
- `http://localhost:4200` (Angular)

**Frontend must NOT modify CORS headers**

---

## 9. Best Practices

### 9.1 State Management

**Recommended state to track:**
- `query` - Current user input
- `response` - Classification response from backend
- `loading` - Whether request is in progress
- `error` - Error message (if any)
- `steps` - Map of step executions by step ID
- `executingSteps` - Set of currently executing step IDs

### 9.2 Debouncing

**For auto-complete or preview (future):**
- Debounce user input by 300-500ms
- Prevents excessive API calls while typing
- Improves performance and reduces backend load

### 9.3 Loading States

**Track loading at multiple levels:**
- Global loading (query submission)
- Per-step loading (individual step execution)
- Clear loading state after completion or error
- Disable UI interactions during loading

### 9.4 Optimistic Updates

**For better UX:**
1. Immediately update UI to show "Running" state
2. Execute actual API request
3. Update UI with real result
4. Rollback if request fails

### 9.5 Accessibility

**Requirements:**
- Keyboard navigation (Tab, Enter, Escape)
- ARIA labels for interactive elements
- Screen reader support
- Focus management
- Color contrast (WCAG AA)
- Disabled state indication

### 9.6 Responsive Design

**Breakpoints:**
```css
/* Mobile */
@media (max-width: 768px) {
  .response-sections-horizontal {
    flex-direction: column;
  }
}

/* Tablet */
@media (min-width: 769px) and (max-width: 1024px) {
  .container {
    max-width: 90%;
  }
}

/* Desktop */
@media (min-width: 1025px) {
  .container {
    max-width: 1200px;
  }
}
```

---

## 10. Testing Requirements

### Unit Tests

**Test:**
- API request/response formatting
- State management logic
- Auto-execution conditions
- Error handling

### Integration Tests

**Test:**
- Full user flow (query → classification → execution)
- Error scenarios
- Network failures
- Authentication failures

### E2E Tests - QA Automation Team

**Test with your preferred tool (Playwright, Cypress, Selenium):**
1. Navigate to application
2. Enter query in textarea
3. Submit form
4. Verify response displayed
5. Verify steps rendered
6. Test step execution

---

## 11. Performance Optimization

### Code Splitting

- Lazy load heavy components
- Reduce initial bundle size
- Show loading state while loading chunks

### Component Re-rendering

- Prevent unnecessary re-renders
- Memoize expensive computations
- Use appropriate comparison strategies

### API Caching

- Cache classification results for identical queries
- Use in-memory cache or browser storage
- Set appropriate TTL (time-to-live)

---

## 12. Deployment

### Build for Production

**Build optimizations:**
- Minify JavaScript/CSS
- Remove console logs
- Enable tree shaking
- Compress assets

### Environment Variables

**Required:**
- `API_BASE_URL` - Backend API endpoint
- `AUTH_DOMAIN` - Authentication service
- `CLIENT_ID` - OAuth client ID (if using OAuth)

---

## 13. HTTP Client Requirements

### API Service Layer

**Create an API client that:**
- Handles authentication headers
- Manages base URL configuration
- Implements error handling
- Provides type-safe request/response (if using TypeScript)

**Must support:**
- POST `/api/v1/process` - Process natural language query
- POST `/api/v1/execute-step` - Execute runbook step
- GET `/api/v1/health` - Health check (optional)

---

## 14. Security Considerations

### Input Sanitization

**Sanitize user input before display:**
- Escape HTML special characters
- Trim whitespace
- Validate format

### XSS Prevention

- Use framework's built-in escaping
- Never render raw HTML from user input
- Validate all user input
- Use Content Security Policy (CSP)

### CSRF Protection

- Use idempotency keys
- Validate origin headers
- SameSite cookies

---

## 15. Monitoring & Analytics

### Track Events

**Recommended analytics events in future:**
- `query_submitted` - User submits query
- `step_executed` - Step execution initiated
- `step_completed` - Step execution completed
- `error_occurred` - Error during operation

### Error Logging

**Send errors to monitoring service:**
- Log error details
- Include context (user ID, query, step)
- Track error frequency
- Set up alerts for critical errors

---

**Questions?** Contact the Production Support team.

