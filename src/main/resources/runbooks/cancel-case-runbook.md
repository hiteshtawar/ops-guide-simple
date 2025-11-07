# Cancel Case Runbook

**Task ID**: CANCEL_CASE  
**Version**: 3.1  
**Last Updated**: 2024-12-01  
**Service**: Case Management  

## Overview

Complete cancellation of a case including cleanup of associated workflows, notifications, and related records.

## Pre-checks

1. **Verify User Has Cancel Permission**
   ```bash
   GET /api/v1/users/{user_id}/permissions/cancel_case
   ```

2. **Verify Case Exists**
   ```bash
   GET /api/v2/cases/{case_id}
   ```

3. **Check Case Not Already Cancelled**
   ```bash
   GET /api/v2/cases/{case_id}/status
   ```

## Procedure

1. **Preview Cancellation Impact**
   ```bash
   GET /api/v2/cases/{case_id}/cancel/preview
   Headers:
     Authorization: Bearer {token}
     X-User-ID: {user_id}
   ```

2. **Execute Case Cancellation**
   ```bash
   POST /api/v2/cases/{case_id}/cancel
   Headers:
     Authorization: Bearer {token}
     X-User-ID: {user_id}
     X-Idempotency-Key: {IDEMPOTENCY_KEY}
     Content-Type: application/json
   
   Body:
   {
     "reason": "operational_request",
     "notes": "Cancelled via OpsGuide",
     "notify_stakeholders": true
   }
   ```


## Post-checks

1. **Verify Case Status Updated to Cancelled**
   ```bash
   GET /api/v2/cases/{case_id}/status
   ```

2. **Verify Audit Log Entry Created**
   ```bash
   GET /api/v2/cases/{case_id}/audit-log
   ```

## Error Handling

**Common Errors:**

- `400 Bad Request - Invalid case state`: Check pre-conditions, case may already be cancelled or closed
- `403 Forbidden - Insufficient permissions`: Verify user roles and case ownership
- `409 Conflict - Active hold or dependencies`: Resolve dependencies first (e.g., related orders, alerts)
- `429 Too Many Requests`: Wait 60 seconds and retry with same idempotency key

**Escalation Path:**
- Level 1: Retry with exponential backoff
- Level 2: Check case dependencies and resolve blocks
- Level 3: Contact connect team via #case-ops channel

## Risk Assessment

**Risk Level**: Medium

**Potential Impact**:
- Related material may be affected if case cancellation propagates
- Customer notifications triggered
- Downstream system cleanup needed
- Historical case data preserved for audit

**Mitigation**:
- Preview step shows impact before execution
- 2-hour rollback window available
- Full audit trail maintained
- Automatic dependency checks prevent orphaned records
