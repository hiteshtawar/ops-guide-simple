# Cancel Case Runbook

**Task ID**: CANCEL_CASE  
**Version**: 3.1  
**Last Updated**: 2024-12-01  
**Service**: Case Management  

## Overview

Complete cancellation of a case including cleanup of associated workflows, notifications, and related records.

## Pre-checks

1. **Authorization Check**
   ```bash
   # Verify user has case_admin or ops_engineer role
   GET /api/v2/users/{user_id}/roles
   ```

2. **Case Status Validation**
   ```bash
   # Case must be in cancellable state
   GET /api/v2/cases/{case_id}/status
   # Valid states: pending, accessioning, grossing, embedding, cutting, staining, microscopy, under_review, on_hold
   # Invalid states: completed, cancelled, archived, closed
   ```

3. **Business Rule Checks**
   ```bash
   # Check for active dependencies or blocking issues
   GET /api/v2/cases/{case_id}/dependencies
   # Ensure no active_hold: true
   # Ensure related_orders_status != "in_progress"
   # Verify no critical alerts attached
   ```

4. **Cancellation Window**
   ```bash
   # Verify within business hours (if required)
   # Check case age < 30 days for automatic approval
   GET /api/v2/cases/{case_id}/metadata
   ```

## Procedure

1. **Generate Idempotency Key**
   ```bash
   IDEMPOTENCY_KEY=$(uuidgen)
   ```

2. **Preview Cancellation Impact**
   ```bash
   GET /api/v2/cases/{case_id}/cancel/preview
   Headers:
     Authorization: Bearer {token}
     X-User-ID: {user_id}
   ```

3. **Execute Cancellation**
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

4. **Wait for Async Processing**
   ```bash
   # Poll status every 30 seconds, max 5 minutes
   GET /api/v2/cases/{case_id}/cancel/status
   # Expected: status="processing" -> status="completed"
   ```

## Rollback Procedure

**Within 2 hours of cancellation:**

1. **Check Reinstatement Eligibility**
   ```bash
   GET /api/v2/cases/{case_id}/reinstate/eligibility
   ```

2. **Reinstate Case** (if eligible)
   ```bash
   POST /api/v2/cases/{case_id}/reinstate
   Headers:
     Authorization: Bearer {token}
     X-User-ID: {user_id}
     X-Idempotency-Key: {new_uuid}
   
   Body:
   {
     "reason": "rollback_cancellation",
     "restore_previous_state": true
   }
   ```

**After 2 hours:**
- Reinstatement requires manual intervention
- Create remediation ticket with template: CASE-REINSTATE
- Include original case data from audit log

## Post-checks

1. **Verify Case Status**
   ```bash
   GET /api/v2/cases/{case_id}/status
   # Expected: status="cancelled"
   ```

2. **Check Downstream Systems**
   ```bash
   # Verify related orders updated (if applicable)
   GET /api/v2/cases/{case_id}/related-orders/status
   
   # Verify notification sent
   GET /api/v2/notifications/cases/{case_id}/history
   
   # Verify case notes updated
   GET /api/v2/cases/{case_id}/notes
   ```

3. **Audit Log Entry**
   ```bash
   GET /api/v2/cases/{case_id}/audit-log
   # Verify cancellation event recorded with user_id and timestamp
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
- Level 3: Contact case management team via #case-ops channel

## Risk Assessment

**Risk Level**: Medium

**Potential Impact**:
- Related orders may be affected if case cancellation propagates
- Customer notifications triggered
- Downstream system cleanup needed
- Historical case data preserved for audit

**Mitigation**:
- Preview step shows impact before execution
- 2-hour rollback window available
- Full audit trail maintained
- Automatic dependency checks prevent orphaned records
