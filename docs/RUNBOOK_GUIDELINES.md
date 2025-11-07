# Runbook Guidelines
## How to Write Proper Runbooks for Production Support

**Version:** 1.0  
**Last Updated:** November 7, 2025

---

## Runbook Structure

### Required Sections

```markdown
# [Operation Name] Runbook

**Task ID**: [TASK_TYPE_ENUM]  
**Version**: [X.Y]  
**Last Updated**: [YYYY-MM-DD]

## Overview
Brief description of what this operation does.

## Pre-checks
Steps to verify before executing the main operation.

## Procedure
Main operation steps.

## Post-checks
Verification steps after the operation.

## Rollback
Steps to rollback if something goes wrong.
```

---

## Step Format (IMPORTANT!)

### ✅ CORRECT Format

```markdown
## Pre-checks

1. **Verify Case Exists**
   ```bash
   GET /api/v2/cases/{case_id}
   ```

2. **Check Case Not Already Cancelled**
   ```bash
   GET /api/v2/cases/{case_id}/status
   # Expected: status != "cancelled"
   ```
```

### ❌ WRONG Format

```markdown
## Pre-checks

1. Verify Case Exists (missing **)
   ```bash
   GET /api/v2/cases/{case_id}
   ```

2. **Check Case Not Already Cancelled (missing closing **)
   ```bash
   GET /api/v2/cases/{case_id}/status
   ```
```

---

## Step Naming Rules

### Required Format

```
[Number]. **[Step Name]**
   ```[language]
   [API Call or Command]
   ```
```

**Pattern:** `1. **Step Name Here**`

**Rules:**
1. Start with number and period: `1.`
2. **MUST** have `**` before and after step name
3. Followed by indented code block
4. No blank line between step name and code block

### Examples

```markdown
1. **Verify User Permissions**
   ```bash
   GET /api/v1/users/{user_id}/roles
   ```

2. **Get Current Case Status**
   ```bash
   GET /api/v2/cases/{case_id}
   ```

3. **Cancel the Case**
   ```bash
   POST /api/v2/cases/{case_id}/cancel
   Headers:
     Authorization: Bearer {token}
   
   Body:
   {
     "reason": "operational_request"
   }
   ```
```

---

## API Call Format

### HTTP Methods Supported

- `GET` - Retrieve data
- `POST` - Create or execute
- `PATCH` - Partial update
- `PUT` - Full update
- `DELETE` - Remove

### Basic API Call

```markdown
1. **Step Name**
   ```bash
   GET /api/v2/cases/{case_id}
   ```
```

### API Call with Headers

```markdown
2. **Cancel Case with Auth**
   ```bash
   POST /api/v2/cases/{case_id}/cancel
   Headers:
     Authorization: Bearer {token}
     X-User-ID: {user_id}
     X-Idempotency-Key: {idempotency_key}
   ```
```

### API Call with Request Body

```markdown
3. **Update Case Status**
   ```bash
   PATCH /api/v2/cases/{case_id}
   Headers:
     Authorization: Bearer {token}
     Content-Type: application/json
   
   Body:
   {
     "status": "cancelled",
     "reason": "operational_request",
     "notes": "Cancelled via Production Support"
   }
   ```
```

---

## Entity Placeholders

### Supported Placeholders

| Placeholder | Description | Example Value |
|-------------|-------------|---------------|
| `{case_id}` | Case identifier | `2025123P6732` |
| `{user_id}` | User identifier | `engineer@example.com` |
| `{status}` | Target status | `pending`, `grossing` |
| `{token}` | Auth token | `eyJhbGc...` |
| `{idempotency_key}` | Unique request ID | `uuid` |

### Usage in URLs

```markdown
GET /api/v2/cases/{case_id}
PATCH /api/v2/cases/{case_id}/status
```

### Usage in Request Bodies

```markdown
Body:
{
  "case_id": "{case_id}",
  "new_status": "{status}",
  "updated_by": "{user_id}"
}
```

---

## Step Types (Sections)

### Pre-checks
**Purpose:** Validate preconditions before execution

**Characteristics:**
- `autoExecutable: true` (usually)
- Non-destructive operations
- GET requests or validation logic

**Best Practice:** Use preview/validation APIs that combine multiple checks

**Example:**
```markdown
## Pre-checks

1. **Verify User Has Permissions**
   ```bash
   GET /api/v1/users/{user_id}/permissions/cancel_case
   ```

2. **Preview Operation Impact**
   ```bash
   GET /api/v2/cases/{case_id}/cancel/preview
   Headers:
     Authorization: Bearer {token}
   ```

**Note:** The preview API validates that:
- Case exists
- Case is not already cancelled
- No blocking dependencies exist
```

**Why?** Consolidate multiple validation checks into one API call for efficiency.

### Procedure
**Purpose:** Main operation steps

**Characteristics:**
- `autoExecutable: false` (requires approval)
- Destructive or critical operations
- POST, PATCH, DELETE requests

**Example:**
```markdown
## Procedure

1. **Cancel the Case**
   ```bash
   POST /api/v2/cases/{case_id}/cancel
   Headers:
     Authorization: Bearer {token}
   
   Body:
   {
     "reason": "operational_request"
   }
   ```
```

### Post-checks
**Purpose:** Verify operation succeeded

**Characteristics:**
- `autoExecutable: true` (usually)
- Verification steps
- GET requests

**Example:**
```markdown
## Post-checks

1. **Verify Case Cancelled**
   ```bash
   GET /api/v2/cases/{case_id}
   # Expected: status="cancelled"
   ```
```

### Rollback
**Purpose:** Undo changes if operation fails

**Example:**
```markdown
## Rollback

1. **Restore Original Status**
   ```bash
   PATCH /api/v2/cases/{case_id}
   Body:
   {
     "status": "{original_status}"
   }
   ```
```

---

## Common Mistakes to Avoid

### ❌ Mistake 1: Missing `**` Around Step Name

```markdown
1. Verify Case Exists  ❌
   ```bash
   GET /api/v2/cases/{case_id}
   ```
```

**Fix:**
```markdown
1. **Verify Case Exists**  ✅
   ```bash
   GET /api/v2/cases/{case_id}
   ```
```

### ❌ Mistake 2: Blank Line Between Step Name and Code Block

```markdown
1. **Verify Case Exists**
                          ← Extra blank line
   ```bash
   GET /api/v2/cases/{case_id}
   ```
```

**Fix:**
```markdown
1. **Verify Case Exists**
   ```bash  ← No blank line
   GET /api/v2/cases/{case_id}
   ```
```

### ❌ Mistake 3: Missing HTTP Method

```markdown
1. **Get Case**
   ```bash
   /api/v2/cases/{case_id}  ❌ Missing GET
   ```
```

**Fix:**
```markdown
1. **Get Case**
   ```bash
   GET /api/v2/cases/{case_id}  ✅
   ```
```

### ❌ Mistake 4: Incorrect Indentation

```markdown
1. **Get Case**
```bash  ❌ Not indented
GET /api/v2/cases/{case_id}
```
```

**Fix:**
```markdown
1. **Get Case**
   ```bash  ✅ 3 spaces indent
   GET /api/v2/cases/{case_id}
   ```
```

### ❌ Mistake 5: Mixing Comments with API Path

```markdown
1. **Get Case**
   ```bash
   # Verify user has permissions  ← Comment becomes description
   GET /api/v1/users/{user_id}/roles
   ```
```

**Fix:** Use step name for description, not comments
```markdown
1. **Verify User Has Permissions**
   ```bash
   GET /api/v1/users/{user_id}/roles
   ```
```

---

## Parser Behavior

### What Gets Extracted

```markdown
1. **Verify Case Exists**
   ```bash
   GET /api/v2/cases/{case_id}
   Headers:
     Authorization: Bearer {token}
   
   Body:
   {
     "filter": "active"
   }
   ```
```

**Extracted as:**
```json
{
  "stepNumber": 1,
  "description": "Verify Case Exists",
  "method": "GET",
  "path": "/api/v2/cases/{case_id}",
  "requestBody": "Headers:\n  Authorization: Bearer {token}\n\nBody:\n{\n  \"filter\": \"active\"\n}",
  "expectedResponse": null,
  "autoExecutable": true,
  "stepType": "precheck"
}
```

### Step Description Extraction

**Pattern:** `^\d+\.\s+\*\*(.+)\*\*$`

**Matches:**
- `1. **Step Name**` ✅
- `2. **Another Step**` ✅

**Does NOT Match:**
- `1. Step Name` ❌ (missing **)
- `1. **Step Name` ❌ (missing closing **)
- `**Step Name**` ❌ (missing number)

---

## Complete Example

### cancel-case-runbook.md

```markdown
# Cancel Case Runbook

**Task ID**: CANCEL_CASE  
**Version**: 1.0  
**Last Updated**: 2025-11-07

## Overview
Cancel a pathology case with proper validation and cleanup.

## Pre-checks

1. **Verify User Has Cancel Permission**
   ```bash
   GET /api/v1/users/{user_id}/permissions
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

1. **Cancel the Case**
   ```bash
   POST /api/v2/cases/{case_id}/cancel
   Headers:
     Authorization: Bearer {token}
     X-User-ID: {user_id}
     Content-Type: application/json
   
   Body:
   {
     "reason": "operational_request",
     "notes": "Cancelled via Production Support"
   }
   ```

## Post-checks

1. **Verify Case Status Updated**
   ```bash
   GET /api/v2/cases/{case_id}
   ```

2. **Verify Stakeholders Notified**
   ```bash
   GET /api/v2/cases/{case_id}/notifications
   ```

## Rollback

1. **Restore Case to Active**
   ```bash
   PATCH /api/v2/cases/{case_id}/status
   Body:
   {
     "status": "active",
     "reason": "rollback_from_cancellation"
   }
   ```
```

---

## Validation Checklist

Before committing a runbook, verify:

- [ ] Step names wrapped in `**Step Name**`
- [ ] Each step has number: `1.`, `2.`, etc.
- [ ] Code blocks properly indented (3 spaces)
- [ ] HTTP method specified: GET, POST, PATCH, etc.
- [ ] API paths start with `/api/`
- [ ] Placeholders use `{variable_name}` format
- [ ] Sections exist: Pre-checks, Procedure, Post-checks
- [ ] No typos in API paths or placeholders
- [ ] Headers and Body properly formatted

---

## Testing Your Runbook

### 1. Restart Server

```bash
cd /Users/hiteshtawar/ops-guide-simple
./start-server.sh
```

### 2. Check Logs

Look for:
```
Loading runbook: CANCEL_CASE from runbooks/cancel-case-runbook.md
Loaded X steps for CANCEL_CASE
```

### 3. Test API

```bash
curl http://localhost:8093/api/v1/process \
  -H "Content-Type: application/json" \
  -d '{"query": "cancel case 2025P1234", "userId": "test"}'
```

### 4. Verify Response

Check that:
- Steps have proper descriptions (not "API call" or comments)
- All steps have correct method and path
- stepType is correct (precheck, procedure, postcheck, rollback)

---

## Quick Fix for Your Current Issue

### Current (Wrong)
```markdown
1. Authorization Check
   ```bash
   GET /api/v1/users/{user_id}/roles
   ```
```

### Fixed (Correct)
```markdown
1. **Verify User Has Cancel Permission**
   ```bash
   GET /api/v1/users/{user_id}/permissions/cancel_case
   ```
```

**Changes:**
1. Added `**` around step name
2. Made step name descriptive (not just "Authorization Check")
3. Fixed API path typo (`cacncel_case` → `cancel_case`)
4. Removed comment line (description comes from step name)

---

## Summary

✅ **DO:**
- Use `1. **Step Name**` format
- Keep step names descriptive
- Indent code blocks with 3 spaces
- Start API paths with HTTP method
- Use clear placeholder names

❌ **DON'T:**
- Omit `**` markers
- Use generic names like "API call"
- Put description in comments
- Have blank lines between step name and code block
- Mix different indentation styles

---

**Questions?** Check existing runbooks for reference:
- `src/main/resources/runbooks/cancel-case-runbook.md`
- `src/main/resources/runbooks/update-case-status-runbook.md`

**Last Updated:** November 7, 2025

