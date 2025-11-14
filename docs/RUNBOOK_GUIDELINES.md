# Runbook Guidelines
## How to Write YAML Runbooks for Production Support

**Version:** 2.0  
**Last Updated:** November 14, 2025

---

## Overview

Runbooks are defined in **YAML format** and stored in `src/main/resources/runbooks/`. The system automatically discovers and loads all YAML files on startup—no code changes required.

**Prerequisite:** Downstream services must have **PATCH, POST, or DELETE APIs** ready for the steps to be executed.

---

## Runbook Structure

### Complete YAML Schema

```yaml
useCase:
  id: "UNIQUE_TASK_ID"
  name: "Human Readable Name"
  description: "What this operation does"
  category: "category-name"
  version: "1.0"
  downstreamService: "service-name"

classification:
  keywords:
    - "primary keyword"
    - "alternative keyword"
  synonyms:
    word1: ["synonym1", "synonym2"]
  requiredEntities:
    - entity1
    - entity2
  minConfidence: 1.0

extraction:
  entities:
    entity1:
      type: "string"
      patterns:
        - "regex pattern with (capture group)"
      required: true
      validation:
        regex: "^pattern$"
        enumValues: ["value1", "value2"]
        errorMessage: "Validation error message"
      transform: "lowercase" # optional: lowercase, uppercase, trim

execution:
  timeout: 30
  retryPolicy:
    maxAttempts: 3
    backoffMs: 1000
  
  steps:
    - stepNumber: 1
      stepType: "prechecks" # or "procedure", "postchecks", "rollback"
      name: "Step Name"
      method: "GET" # GET, POST, PATCH, PUT, DELETE, LOCAL_MESSAGE, HEADER_CHECK
      path: "/api/path/{placeholder}"
      body: # Optional, can be string or object
        key: "{placeholder}"
      expectedResponse: "Expected value" # For HEADER_CHECK
      message: "Error message if step fails"
      autoExecutable: true

localMessage: "Message shown before execution"
warnings:
  - "Warning message 1"
  - "Warning message 2"
metadata:
  owner: "team-name"
  tags: ["tag1", "tag2"]
```

---

## Step Types

### Prechecks (`stepType: "prechecks"`)

**Purpose:** Validate preconditions before execution

**Characteristics:**
- Usually `autoExecutable: true`
- Non-destructive operations
- GET requests or validation logic

**Example:**
```yaml
- stepNumber: 1
  stepType: "prechecks"
  name: "Verify User Has Permission"
  method: "HEADER_CHECK"
  path: "Role-Name"
  expectedResponse: "Production Support"
  autoExecutable: true
```

### Procedure (`stepType: "procedure"`)

**Purpose:** Main operation steps

**Characteristics:**
- Usually `autoExecutable: false` (requires approval)
- Destructive or critical operations
- POST, PATCH, DELETE requests

**Example:**
```yaml
- stepNumber: 2
  stepType: "procedure"
  name: "Cancel the Case"
  method: "DELETE"
  path: "/lims-api/cases/{case_id}"
  message: "Failed to cancel case {case_id}"
  autoExecutable: false
```

### Postchecks (`stepType: "postchecks"`)

**Purpose:** Verify operation succeeded

**Characteristics:**
- Usually `autoExecutable: true`
- Verification steps
- GET requests

**Example:**
```yaml
- stepNumber: 3
  stepType: "postchecks"
  name: "Verify Case Cancelled"
  method: "GET"
  path: "/lims-api/cases/{case_id}"
  autoExecutable: true
```

### Rollback (`stepType: "rollback"`)

**Purpose:** Undo changes if operation fails

**Example:**
```yaml
- stepNumber: 4
  stepType: "rollback"
  name: "Restore Original Status"
  method: "PATCH"
  path: "/lims-api/cases/{case_id}/status"
  body:
    status: "{original_status}"
```

---

## HTTP Methods

### Supported Methods

| Method | Use Case | Example |
|--------|----------|---------|
| `GET` | Retrieve data | `GET /api/cases/{case_id}` |
| `POST` | Create or execute | `POST /api/cases/{case_id}/cancel` |
| `PATCH` | Partial update | `PATCH /api/cases/{case_id}/status` |
| `PUT` | Full update | `PUT /api/cases/{case_id}` |
| `DELETE` | Remove | `DELETE /api/cases/{case_id}` |
| `LOCAL_MESSAGE` | Display message | No API call |
| `HEADER_CHECK` | Validate header | Check `Role-Name` header |

### Method Examples

**GET:**
```yaml
- stepNumber: 1
  method: "GET"
  path: "/lims-api/cases/{case_id}"
```

**POST with Body:**
```yaml
- stepNumber: 2
  method: "POST"
  path: "/lims-api/cases/{case_id}/cancel"
  body:
    reason: "operational_request"
    notes: "Cancelled via Production Support"
```

**PATCH with Body:**
```yaml
- stepNumber: 3
  method: "PATCH"
  path: "/lims-api/samples/{barcode}/status"
  body:
    sampleStatus: "{sampleStatus}"
```

**LOCAL_MESSAGE:**
```yaml
- stepNumber: 1
  method: "LOCAL_MESSAGE"
  name: "Display Warning"
  message: "This operation will cancel the case permanently"
```

**HEADER_CHECK:**
```yaml
- stepNumber: 1
  method: "HEADER_CHECK"
  path: "Role-Name"
  expectedResponse: "Production Support"
```

---

## Entity Extraction

### Pattern Definition

```yaml
extraction:
  entities:
    case_id:
      type: "string"
      patterns:
        - "(?:a\\s+|the\\s+)?case\\s+(\\d{4,}\\w+)" # Handles "a case", "the case"
        - "(\\d{7}[A-Z]\\d{4})" # Format: 2025123P6732
      required: true
      validation:
        regex: "^\\d{4,}\\w*$"
        errorMessage: "Case ID must be in format: 2025123P6732"
```

### Pattern Best Practices

1. **Use capture groups:** `(\\d{7}[A-Z]\\d{4})` captures the value
2. **Handle variations:** `(?:a\\s+|the\\s+)?` handles optional articles
3. **Multiple patterns:** Provide fallback patterns for different formats
4. **Validation:** Always validate extracted values with regex

### Transform Options

```yaml
transform: "lowercase"  # Convert to lowercase
transform: "uppercase"  # Convert to uppercase
transform: "trim"       # Remove leading/trailing whitespace
```

---

## Classification

### Keywords

```yaml
classification:
  keywords:
    - "cancel case"
    - "delete case"
    - "abort case"
  synonyms:
    cancel: ["delete", "abort", "remove"]
    case: ["a case", "the case"]
  minConfidence: 1.0
```

**Best Practices:**
- Include common variations
- Use synonyms for flexibility
- Set `minConfidence` to avoid false positives

---

## Placeholders

### Supported Placeholders

Placeholders in paths and bodies are replaced with extracted entities:

```yaml
path: "/lims-api/cases/{case_id}"
body:
  sampleId: "{barcode}"
  status: "{sampleStatus}"
```

**Example:**
- Query: `"cancel case 2025123P6732"`
- Extracted: `{case_id: "2025123P6732"}`
- Resolved path: `/lims-api/cases/2025123P6732`

---

## Complete Example

### cancel-case.yaml

```yaml
useCase:
  id: "CANCEL_CASE"
  name: "Cancel Case"
  description: "Complete cancellation of a case including cleanup"
  category: "case-management"
  version: "3.1"
  downstreamService: "ap-services"

classification:
  keywords:
    - "cancel case"
    - "delete case"
    - "abort case"
  synonyms:
    cancel: ["delete", "abort", "remove"]
    case: ["a case", "the case"]
  requiredEntities:
    - case_id

extraction:
  entities:
    case_id:
      type: "string"
      patterns:
        - "(?:a\\s+|the\\s+)?case\\s+(\\d{4,}\\w+)"
        - "(\\d{7}[A-Z]\\d{4})"
      required: true
      validation:
        regex: "^\\d{4,}\\w*$"
        errorMessage: "Case ID must be in format: 2025123P6732"

execution:
  timeout: 30
  steps:
    - stepNumber: 1
      stepType: "prechecks"
      name: "Verify User Has Cancel Permission"
      method: "HEADER_CHECK"
      path: "Role-Name"
      expectedResponse: "Production Support"
      autoExecutable: true
    
    - stepNumber: 2
      stepType: "procedure"
      name: "Cancel the Case"
      method: "DELETE"
      path: "/lims-api/cases/{case_id}"
      message: "Failed to cancel case {case_id}"
      autoExecutable: false
    
    - stepNumber: 3
      stepType: "postchecks"
      name: "Verify Case Cancelled"
      method: "GET"
      path: "/lims-api/cases/{case_id}"
      autoExecutable: true

localMessage: "Case will be permanently cancelled and removed from workpool"
warnings:
  - "This action cannot be undone"
  - "All associated materials will be cancelled"
```

---

## Validation Checklist

Before committing a runbook, verify:

- [ ] `useCase.id` is unique and follows `UPPER_SNAKE_CASE`
- [ ] All required sections present: `useCase`, `classification`, `execution`
- [ ] `stepNumber` is sequential (1, 2, 3...)
- [ ] `stepType` is one of: `prechecks`, `procedure`, `postchecks`, `rollback`
- [ ] `method` is valid: `GET`, `POST`, `PATCH`, `PUT`, `DELETE`, `LOCAL_MESSAGE`, `HEADER_CHECK`
- [ ] `path` uses placeholders like `{entity_name}`
- [ ] All placeholders have corresponding extraction patterns
- [ ] `requiredEntities` matches extraction entity names
- [ ] Validation regex patterns are correct
- [ ] YAML syntax is valid (use a YAML validator)

---

## Testing Your Runbook

### 1. Validate YAML Syntax

```bash
# Install yamllint
pip install yamllint

# Validate
yamllint src/main/resources/runbooks/your-runbook.yaml
```

### 2. Restart Server

```bash
cd /Users/hiteshtawar/ops-guide-simple
./start-server.sh
```

### 3. Check Logs

Look for:
```
Loading runbooks from: classpath:runbooks/
Successfully loaded 3 runbooks: [CANCEL_CASE, UPDATE_SAMPLE_STATUS, YOUR_TASK]
```

### 4. Test Classification

```bash
curl -X POST http://localhost:8093/api/v1/classify \
  -H "Content-Type: application/json" \
  -d '{"query": "your test query"}'
```

### 5. Test Full Flow

```bash
curl -X POST http://localhost:8093/api/v1/process \
  -H "Content-Type: application/json" \
  -d '{
    "query": "your test query with entities",
    "userId": "test@example.com",
    "downstreamService": "ap-services"
  }'
```

---

## Common Mistakes

### ❌ Missing Required Fields

```yaml
useCase:
  id: "MY_TASK"
  # Missing: name, description, downstreamService
```

**Fix:**
```yaml
useCase:
  id: "MY_TASK"
  name: "My Task"
  description: "What this does"
  downstreamService: "ap-services"
```

### ❌ Invalid Step Type

```yaml
stepType: "precheck"  # ❌ Should be "prechecks"
```

**Fix:**
```yaml
stepType: "prechecks"  # ✅
```

### ❌ Placeholder Not Extracted

```yaml
path: "/api/{case_id}"  # ❌ case_id not in extraction.entities
```

**Fix:**
```yaml
extraction:
  entities:
    case_id:  # ✅ Add extraction pattern
      patterns:
        - "(\\d{7}[A-Z]\\d{4})"
```

### ❌ Missing Validation

```yaml
entities:
  case_id:
    patterns: ["(.*)"]  # ❌ Too permissive
```

**Fix:**
```yaml
entities:
  case_id:
    patterns: ["(\\d{7}[A-Z]\\d{4})"]
    validation:
      regex: "^\\d{7}[A-Z]\\d{4}$"
```

---

## Best Practices

✅ **DO:**
- Use descriptive step names
- Include validation for all entities
- Test with various query formats
- Use synonyms for flexibility
- Set appropriate `autoExecutable` flags
- Include error messages for steps

❌ **DON'T:**
- Use generic step names like "API call"
- Skip validation
- Hard-code values (use placeholders)
- Mix step types incorrectly
- Use invalid HTTP methods

---

## Reference Examples

See existing runbooks for reference:
- `src/main/resources/runbooks/cancel-case.yaml`
- `src/main/resources/runbooks/update-sample-status.yaml`

---

**Last Updated:** November 14, 2025
