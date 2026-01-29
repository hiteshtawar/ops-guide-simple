# Connect Assist - User Tutorial

## Quick Start Guide

### Overview
Connect Assist helps production support users resolve service tickets and unblock cases to complete workflows.

---

## Step-by-Step Instructions

### 1. **Login**
- Navigate to Connect application
- Sign in with your credentials

### 2. **Select Role**
- Landing page displays available roles:
  - LMSNC - Pathologist
  - LMSNC - Production Support
  - LMSNC - System Administrator
  - SUCVA - Pathologist
  - SUCVA - System Administrator
  - YOIHC - Lab Assistant
- **Select: Production Support**

### 3. **Access Connect Assist**
- Production Support role unlocks Connect Assist features
- Main menu displays operation options

### 4. **Choose Operation**
Available operations:
- Cancel Case
- Clear Storage Unit
- Create Workpool Entry
- Delete Workpool Entry
- Reconcile Storage Unit Occupied Count
- Update Sample Barcode
- Update Sample Status
- Update Stain Name

### 5. **Execute Cancel Case**

**Use Case:** Cancel a case that was cancelled by downstream service (LCLS)

**Steps:**
1. Click **Cancel Case** button
2. Enter case number (e.g., `2025P1234`)
3. Tool performs:
   - **Validation**
     - API verifies user has Production Support role (flow stops if not authorized)
     - Preview cancellation impact: materials and case will be canceled
   - **Execution**
     - API cancels case and associated materials
     - Case removed from workpool
   - **Verification**
     - API retrieves case status to confirm cancellation
     - Check audit log entry created for user action
4. Review execution results
5. Click **Retry** if errors occur

---

## Workflow Summary

```
Login → Select Role → Production Support → Choose Operation → Execute → Verify
```

---

## Common Scenarios

### Cancel Case Request
**Trigger:** Downstream service (LCLS) cancels case  
**Action:** Use Cancel Case operation  
**Result:** Case removed from workpool, status updated, audit trail created

---

## Video Tutorial
*[Video will be added here]*

---

## Support
For issues or questions, contact Production Support team.
