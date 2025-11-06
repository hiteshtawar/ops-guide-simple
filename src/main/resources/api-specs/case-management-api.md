# Case Management API Specification

**Version**: 2.1  
**Base URL**: `https://api.example.com/v2`  
**Authentication**: Bearer Token  
**Rate Limits**: 100 requests/minute per user  

## Common Headers

All requests should include:
```
Authorization: Bearer {jwt_token}
X-User-ID: {user_id}
Content-Type: application/json
X-Idempotency-Key: {uuid} (for mutations)
```

## Case Status Operations

### GET /cases/{case_id}/status

Get current case status and metadata.

**Parameters**:
- `case_id` (path, required): ID of the case (e.g., "2024-001")

**Response**:
```json
{
  "case_id": "CASE-2024-001",
  "status": "in_progress",
  "created_at": "2024-12-01T10:00:00Z",
  "updated_at": "2024-12-01T14:30:00Z",
  "assigned_to": "pathologist_123",
  "metadata": {
    "age_days": 5,
    "priority": "normal",
    "environment": "prod"
  }
}
```

**Error Codes**:
- `404`: Case not found
- `403`: Insufficient permissions

---

### PATCH /cases/{case_id}/status

Change case status following business rules.

**Parameters**:
- `case_id` (path, required): ID of the case

**Request Body**:
```json
{
  "status": "completed",
  "reason": "pathologist_signoff",
  "notes": "Case completed successfully",
  "artifacts": {
    "signature": "digital_signature_data",
    "report_id": "report_456"
  }
}
```

**Response**:
```json
{
  "case_id": "CASE-2024-001",
  "previous_status": "in_progress",
  "new_status": "completed",
  "transition_id": "trans_789",
  "timestamp": "2024-12-01T15:00:00Z"
}
```

**Error Codes**:
- `400`: Invalid transition or missing required fields
- `403`: User lacks required role for this transition
- `409`: Concurrent modification or business rule violation
- `422`: Missing required artifacts

---

### POST /cases/{case_id}/cancel

Cancel a case and trigger cleanup workflows.

**Parameters**:
- `case_id` (path, required): ID of the case

**Request Body**:
```json
{
  "reason": "operational_request",
  "notes": "Cancelled due to data quality issues",
  "notify_stakeholders": true
}
```

**Response**:
```json
{
  "case_id": "CASE-2024-001",
  "cancellation_id": "cancel_123",
  "status": "processing",
  "estimated_completion": "2024-12-01T15:05:00Z",
  "rollback_deadline": "2024-12-01T17:00:00Z"
}
```

**Error Codes**:
- `400`: Case in non-cancellable state
- `403`: Insufficient permissions
- `409`: Active hold or dependencies
- `429`: Rate limit exceeded

---

### GET /cases/{case_id}/cancel/preview

Preview the impact of cancelling a case without executing.

**Parameters**:
- `case_id` (path, required): ID of the case

**Response**:
```json
{
  "case_id": "CASE-2024-001",
  "impact_assessment": {
    "billing_affected": true,
    "notifications_count": 3,
    "downstream_systems": ["billing", "reporting", "notifications"],
    "estimated_duration": "2-5 minutes",
    "rollback_available": true,
    "rollback_deadline": "2024-12-01T17:00:00Z"
  },
  "warnings": [
    "This case has active billing records",
    "Customer notifications will be sent"
  ]
}
```

---

### POST /cases/{case_id}/reinstate

Reinstate a cancelled case (within rollback window).

**Parameters**:
- `case_id` (path, required): ID of the case

**Request Body**:
```json
{
  "reason": "rollback_cancellation",
  "restore_previous_state": true,
  "notes": "Cancelled in error"
}
```

**Response**:
```json
{
  "case_id": "CASE-2024-001",
  "reinstatement_id": "reinstate_456",
  "restored_status": "in_progress",
  "timestamp": "2024-12-01T15:30:00Z"
}
```

**Error Codes**:
- `400`: Case not in cancelled state or outside rollback window
- `403`: Insufficient permissions
- `409`: Downstream systems already processed cancellation

---

## Case Dependencies

### GET /cases/{case_id}/dependencies

Get case dependencies and blocking conditions.

**Parameters**:
- `case_id` (path, required): ID of the case

**Response**:
```json
{
  "case_id": "CASE-2024-001",
  "active_hold": false,
  "related_orders_status": "completed",
  "critical_alerts": 0,
  "dependencies": [
    {
      "type": "order",
      "id": "ORDER-2024-001",
      "status": "completed",
      "required": true
    }
  ]
}
```

---

### GET /cases/{case_id}/valid-transitions

Get valid status transitions for a case.

**Parameters**:
- `case_id` (path, required): ID of the case

**Response**:
```json
{
  "case_id": "CASE-2024-001",
  "current_status": "in_progress",
  "valid_transitions": [
    {
      "status": "completed",
      "required_artifacts": ["signature", "report_id"],
      "required_role": "pathologist"
    },
    {
      "status": "on_hold",
      "required_artifacts": [],
      "required_role": "lab_tech"
    }
  ]
}
```

---

## Case Workflow Operations

### GET /cases/{case_id}/workflow-history

Get case workflow history and status changes.

**Parameters**:
- `case_id` (path, required): ID of the case

**Response**:
```json
{
  "case_id": "CASE-2024-001",
  "workflow_history": [
    {
      "status": "pending",
      "timestamp": "2024-12-01T10:00:00Z",
      "user_id": "system",
      "reason": "case_created"
    },
    {
      "status": "in_progress",
      "timestamp": "2024-12-01T10:30:00Z",
      "user_id": "lab_tech_123",
      "reason": "workflow_started"
    }
  ]
}
```

---

### GET /cases/{case_id}/audit-log

Get case audit log for compliance and debugging.

**Parameters**:
- `case_id` (path, required): ID of the case

**Response**:
```json
{
  "case_id": "CASE-2024-001",
  "audit_entries": [
    {
      "event": "status_changed",
      "timestamp": "2024-12-01T15:00:00Z",
      "user_id": "pathologist_123",
      "details": {
        "previous_status": "in_progress",
        "new_status": "completed",
        "reason": "pathologist_signoff"
      }
    }
  ]
}
```

---

## Authentication & Authorization

**Scopes Required**:
- `case:read` - Read case information
- `case:status:write` - Change case status
- `case:cancel` - Cancel cases
- `case:reinstate` - Reinstate cancelled cases

**Role Requirements**:
- `lab_tech` - Basic case operations
- `pathologist` - Medical decisions, sign-offs
- `case_admin` - Administrative operations, cancellations
- `ops_engineer` - System operations, reconciliation
- `qa_reviewer` - Quality assurance workflows

## Rate Limiting

- Standard operations: 100 requests/minute per user
- Status changes: 20 requests/minute per case
- Cancellations: 5 requests/minute per user
- Bulk operations: 10 requests/minute per user

**Rate Limit Headers**:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1701435600
```

## Idempotency

All mutation operations support idempotency via `X-Idempotency-Key` header:
- Use UUID v4 format
- Keys expire after 24 hours
- Same key returns cached response for successful operations
- Failed operations can be retried with same key

## Error Response Format

```json
{
  "error": {
    "code": "INVALID_TRANSITION",
    "message": "Cannot transition from 'completed' to 'pending'",
    "details": {
      "current_status": "completed",
      "requested_status": "pending",
      "valid_transitions": ["under_review"]
    },
    "request_id": "req_123456",
    "timestamp": "2024-12-01T16:30:00Z"
  }
}
```

## Case Status Values

### Workflow Statuses
- `pending` - Case created, awaiting processing
- `accessioning` - Case received and logged
- `grossing` - Tissue processing
- `embedding` - Tissue embedded in paraffin
- `cutting` - Sections cut and mounted
- `staining` - H&E staining
- `microscopy` - Slides reviewed
- `under_review` - Pathologist review
- `on_hold` - Temporarily paused

### Terminal Statuses
- `completed` - Final successful state
- `cancelled` - Cancelled before completion
- `archived` - Archived for storage
- `closed` - Closed after completion
