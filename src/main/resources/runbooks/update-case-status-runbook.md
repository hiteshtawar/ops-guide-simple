# Update Case Status Runbook

**Task ID**: UPDATE_CASE_STATUS  
**Version**: 2.1  
**Last Updated**: 2024-12-01  
**Service**: Case Management  

## Overview

Update case status following business rules and workflow transitions. Ensures proper state management and audit trail.

## Pre-checks

1. **Authorization Check**
   ```bash
   # Verify user has appropriate role for status transition
   GET /api/v2/users/{user_id}/roles
   # Required roles: lab_tech, pathologist, case_admin
   ```

2. **Case Status Validation**
   ```bash
   # Verify case exists and get current status
   GET /api/v2/cases/{case_id}/status
   # Ensure case is not in terminal state (completed, cancelled, archived, closed)
   ```

3. **Status Transition Validation**
   ```bash
   # Check if transition is valid based on current status
   # NOTE: This validation is handled by the Case Management Service
   GET /api/v2/cases/{case_id}/valid-transitions
   # Valid transitions follow workflow: pending → accessioning → grossing → embedding → cutting → staining → microscopy → under_review → completed
   ```

4. **Business Rule Checks**
   ```bash
   # Verify no active holds or blocks
   GET /api/v2/cases/{case_id}/dependencies
   # Check for required artifacts (signatures, reports, etc.)
   GET /api/v2/cases/{case_id}/artifacts
   ```

## Valid Status Transitions

### Primary Workflow
```
pending → accessioning → grossing → embedding → cutting → staining → microscopy → under_review → completed
```

### Additional Transitions
- Any status → `on_hold` (with reason)
- Any status → `cancelled` (with approval)
- `on_hold` → previous status (resume workflow)
- `completed` → `under_review` (quality review)

### Terminal States
- `completed` - Final successful state
- `cancelled` - Cancelled before completion
- `archived` - Archived for long-term storage
- `closed` - Closed after completion

## Procedure

1. **Validate Transition**
   ```bash
   # Check if requested status transition is valid
   GET /api/v2/cases/{case_id}/status
   # Current status must allow transition to target status
   ```

2. **Prepare Update Request**
   ```bash
   # Gather required information
   REASON="pathologist_signoff"  # or appropriate reason
   NOTES="Status updated via OpsGuide"
   ARTIFACTS='{"signature": "digital_signature_data", "report_id": "report_456"}'
   ```

3. **Execute Status Update**
   ```bash
   PATCH /api/v2/cases/{case_id}/status
   Headers:
     Authorization: Bearer {token}
     X-User-ID: {user_id}
     X-Idempotency-Key: {IDEMPOTENCY_KEY}
     Content-Type: application/json
   
   Body:
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

4. **Verify Update**
   ```bash
   # Confirm status change was applied
   GET /api/v2/cases/{case_id}/status
   # Expected: status matches requested value
   ```

## Status-Specific Requirements

### Accessioning
- **Required**: Case received and logged
- **Artifacts**: Receipt confirmation, initial photos
- **Next**: Grossing

### Grossing
- **Required**: Tissue processing completed
- **Artifacts**: Gross description, measurements
- **Next**: Embedding

### Embedding
- **Required**: Tissue embedded in paraffin
- **Artifacts**: Embedding log, block ID
- **Next**: Cutting

### Cutting
- **Required**: Sections cut and mounted
- **Artifacts**: Section count, quality notes
- **Next**: Staining

### Staining
- **Required**: H&E staining completed
- **Artifacts**: Staining protocol, quality control
- **Next**: Microscopy

### Microscopy
- **Required**: Slides reviewed under microscope
- **Artifacts**: Microscopic findings, images
- **Next**: Under Review

### Under Review
- **Required**: Pathologist review completed
- **Artifacts**: Pathologist notes, preliminary diagnosis
- **Next**: Completed

### Completed
- **Required**: Final diagnosis and report
- **Artifacts**: Final report, digital signature
- **Next**: Terminal state

## Error Handling

**Common Errors:**

- `400 Bad Request - Invalid transition`: Check if current status allows transition to target status
- `403 Forbidden - Insufficient permissions`: Verify user has required role for this transition
- `409 Conflict - Missing required artifacts`: Provide required artifacts for the status transition
- `422 Unprocessable Entity - Business rule violation`: Check business rules and dependencies

**Escalation Path:**
- Level 1: Verify transition rules and user permissions
- Level 2: Check for missing artifacts or dependencies
- Level 3: Contact case management team for manual override

## Rollback Procedure

**Within 1 hour of status change:**

1. **Check Rollback Eligibility**
   ```bash
   GET /api/v2/cases/{case_id}/rollback/eligibility
   ```

2. **Rollback Status** (if eligible)
   ```bash
   POST /api/v2/cases/{case_id}/rollback
   Headers:
     Authorization: Bearer {token}
     X-User-ID: {user_id}
   
   Body:
   {
     "reason": "rollback_status_change",
     "restore_previous_state": true
   }
   ```

**After 1 hour:**
- Rollback requires manual intervention
- Create remediation ticket with template: CASE-ROLLBACK
- Include original status and reason for rollback

## Post-checks

1. **Verify Status Change**
   ```bash
   GET /api/v2/cases/{case_id}/status
   # Expected: status matches requested value
   ```

2. **Check Audit Trail**
   ```bash
   GET /api/v2/cases/{case_id}/audit-log
   # Verify status change event recorded with user_id and timestamp
   ```

3. **Verify Downstream Updates**
   ```bash
   # Check if downstream systems were notified
   GET /api/v2/cases/{case_id}/notifications
   
   # Verify workflow progression
   GET /api/v2/cases/{case_id}/workflow-history
   ```

## Risk Assessment

**Risk Level**: Low to Medium

**Potential Impact**:
- Incorrect status may affect downstream processes
- Missing artifacts may cause quality issues
- Invalid transitions may break workflow integrity

**Mitigation**:
- Strict transition validation
- Required artifact verification
- Comprehensive audit trail
- Rollback capability within time window
