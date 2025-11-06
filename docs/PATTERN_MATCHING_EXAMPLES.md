# Pattern Matching Examples

This document shows how the improved pattern classifier handles various natural language inputs.

## Test Cases

### Cancel Case Operations

| User Query | Classification | Extracted Entities |
|-----------|---------------|-------------------|
| `please cancel case 2025123P6732` | ✅ CANCEL_CASE | `case_id: 2025123P6732` |
| `delete case 2024123P6731` | ✅ CANCEL_CASE | `case_id: 2024123P6731` |
| `cancel a case 2024123P6731` | ✅ CANCEL_CASE | `case_id: 2024123P6731` |
| `Can you abort case 2024123P6731?` | ✅ CANCEL_CASE | `case_id: 2024123P6731` |
| `remove case 2025123P6732` | ✅ CANCEL_CASE | `case_id: 2025123P6732` |

### Update Case Status Operations

| User Query | Classification | Extracted Entities |
|-----------|---------------|-------------------|
| `update case status to Pending 2024123P6731` | ✅ UPDATE_CASE_STATUS | `case_id: 2024123P6731, status: pending` |
| `Please update case status to Acessioning 2025123P6732` | ✅ UPDATE_CASE_STATUS | `case_id: 2025123P6732, status: accessioning` |
| `Can you mark status to grossing for caseid 2025123P6732` | ✅ UPDATE_CASE_STATUS | `case_id: 2025123P6732, status: grossing` |
| `set status to completed for 2024123P6731` | ✅ UPDATE_CASE_STATUS | `case_id: 2024123P6731, status: completed` |
| `mark as under_review 2025123P6732` | ✅ UPDATE_CASE_STATUS | `case_id: 2025123P6732, status: under_review` |
| `change the status to on hold for case 2024123P6731` | ✅ UPDATE_CASE_STATUS | `case_id: 2024123P6731, status: on_hold` |

## What Was Improved

### 1. Case ID Pattern Recognition
**Before:** Only matched `CASE-2024-001` format  
**After:** Matches multiple formats:
- `2025123P6732` (digits + P + digits)
- `2024123P6731` (digits + P + digits)
- `CASE-2024-001` (legacy format)
- `CASE2024001` (no separators)

### 2. Synonym Support
**Added keywords:**
- Cancel: `delete`, `remove`, `abort`, `terminate`, `drop`
- Update: `mark`, `set`, `change`, `move`, `transition`

### 3. Natural Language Handling
**Automatically filters out:**
- Polite words: `please`, `kindly`, `can you`, `could you`
- Articles: `a`, `an`, `the`
- Filler phrases: `i want to`, `i need to`

**Example transformation:**
```
"please cancel a case 2024123P6731"
↓ (normalized to)
"cancel case 2024123P6731"
```

### 4. Status Normalization
**Handles typos and variations:**
- `Acessioning` → `accessioning` ✅
- `under review` → `under_review` ✅
- `on hold` → `on_hold` ✅
- `Complete` → `completed` ✅

### 5. Flexible Phrasing
**Recognizes various phrasings:**
- `update case status to X`
- `mark status to X`
- `set status X for case Y`
- `mark as X`
- `status to X for caseid Y`

## Testing the Classifier

### Via API

```bash
curl -X POST http://localhost:8093/api/v1/classify \
  -H "Content-Type: application/json" \
  -d '{
    "query": "please cancel case 2025123P6732"
  }'
```

**Expected Response:**
```json
{
  "taskId": "CANCEL_CASE",
  "taskName": "Cancel Case",
  "extractedEntities": {
    "case_id": "2025123P6732"
  }
}
```

### Common Edge Cases

| Query | Result | Note |
|-------|--------|------|
| `cancel` | ❌ UNKNOWN | Missing case ID |
| `2025123P6732` | ❌ UNKNOWN | No action verb |
| `cancel status update` | ❌ UNKNOWN | Ambiguous |
| `2025123P6732 pending` | ✅ UPDATE_CASE_STATUS | Has case_id + status |

## Supported Status Values

- `pending`
- `accessioning` (also matches `accession`)
- `grossing`
- `embedding`
- `cutting`
- `staining`
- `microscopy`
- `under_review` (also matches `review`)
- `on_hold` (also matches `hold`)
- `completed` (also matches `complete`)
- `cancelled` (also matches `cancel`)
- `archived` (also matches `archive`)
- `closed` (also matches `close`)

