# Pattern Matching Examples

This document shows how the YAML-driven pattern classifier handles various natural language inputs.

**Version:** 2.0  
**Last Updated:** November 14, 2025

---

## Test Cases

### Cancel Case Operations

| User Query | Classification | Extracted Entities |
|-----------|---------------|-------------------|
| `please cancel case 2025123P6732` | ✅ CANCEL_CASE | `case_id: 2025123P6732` |
| `delete case 2024123P6731` | ✅ CANCEL_CASE | `case_id: 2024123P6731` |
| `cancel a case 2024123P6731` | ✅ CANCEL_CASE | `case_id: 2024123P6731` |
| `Can you abort case 2024123P6731?` | ✅ CANCEL_CASE | `case_id: 2024123P6731` |
| `remove case 2025123P6732` | ✅ CANCEL_CASE | `case_id: 2025123P6732` |
| `kindly cancel case 2025123P6732` | ✅ CANCEL_CASE | `case_id: 2025123P6732` |
| `cancel the case 2025123P6732` | ✅ CANCEL_CASE | `case_id: 2025123P6732` |

### Update Sample Status Operations

| User Query | Classification | Extracted Entities |
|-----------|---------------|-------------------|
| `update sample status to Ready for - Accessioning for sample BC123456` | ✅ UPDATE_SAMPLE_STATUS | `barcode: BC123456, sampleStatus: Ready for - Accessioning` |
| `update slide status to Completed - Microtomy slide S789012` | ✅ UPDATE_SAMPLE_STATUS | `barcode: S789012, sampleStatus: Completed - Microtomy` |
| `update container status to Completed - Microtomy container C345678` | ✅ UPDATE_SAMPLE_STATUS | `barcode: C345678, sampleStatus: Completed - Microtomy` |
| `update block status to Completed - Microtomy and update workpool block B901234` | ✅ UPDATE_SAMPLE_STATUS | `barcode: B901234, sampleStatus: Completed - Microtomy and update workpool` |
| `change sample status to Hold for sample BC123456` | ✅ UPDATE_SAMPLE_STATUS | `barcode: BC123456, sampleStatus: Hold` |
| `set slide status to In Process slide S789012` | ✅ UPDATE_SAMPLE_STATUS | `barcode: S789012, sampleStatus: In Process` |
| `mark container status to Completed - Microtomy container C345678` | ✅ UPDATE_SAMPLE_STATUS | `barcode: C345678, sampleStatus: Completed - Microtomy` |

---

## What Makes It Work

### 1. Flexible Keyword Matching

**Keywords defined in YAML:**
```yaml
classification:
  keywords:
    - "cancel case"
    - "delete case"
    - "abort case"
    - "update sample status"
    - "update slide status"
```

**Synonyms for flexibility:**
```yaml
synonyms:
  cancel: ["delete", "abort", "remove", "terminate"]
  sample: ["slide", "container", "block", "specimen"]
  update: ["change", "modify", "set", "mark"]
```

### 2. Robust Entity Extraction

**Case ID Patterns:**
```yaml
patterns:
  - "(?:a\\s+|the\\s+)?case\\s+(\\d{4,}\\w+)"  # Handles "a case", "the case"
  - "(\\d{7}[A-Z]\\d{4})"  # Format: 2025123P6732 or 2025123T6732
```

**Barcode Patterns:**
```yaml
patterns:
  - "(?:sample|slide|container|block)\\s+(?:barcode\\s+)?([A-Z0-9]{4,20})"
  - "barcode\\s+([A-Z0-9]{4,20})"
  - "([A-Z0-9]{4,20})"  # Standalone barcode
```

**Status Patterns:**
```yaml
patterns:
  - "to\\s+((?:Ready\\s+for|Completed|Hold|In\\s+Process)(?:\\s*-\\s*[A-Za-z\\s]+)?)"
  - "status\\s+to\\s+((?:Ready\\s+for|Completed|Hold|In\\s+Process)(?:\\s*-\\s*[A-Za-z\\s]+)?)"
```

### 3. Natural Language Handling

**Automatically handles:**
- Polite words: `please`, `kindly`, `can you`, `could you`
- Articles: `a`, `an`, `the`
- Filler phrases: `i want to`, `i need to`

**Example transformation:**
```
"please cancel a case 2024123P6731"
↓ (normalized by patterns)
"cancel case 2024123P6731"
```

### 4. Status Normalization

**Handles complex status formats:**
- `Ready for - Accessioning` ✅
- `Completed - Microtomy` ✅
- `Completed - Microtomy and update workpool` ✅
- `Hold` ✅
- `In Process` ✅

### 5. Flexible Phrasing

**Recognizes various phrasings:**
- `update sample status to X`
- `update slide status to X`
- `change sample status to X`
- `set slide status to X`
- `mark container status to X`
- `status to X for sample Y`

---

## Testing the Classifier

### Via API

**Classification Only:**
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
  "confidence": 2.5
}
```

**Full Processing:**
```bash
curl -X POST http://localhost:8093/api/v1/process \
  -H "Content-Type: application/json" \
  -d '{
    "query": "update sample status to Ready for - Accessioning for sample BC123456",
    "userId": "engineer@example.com",
    "downstreamService": "ap-services"
  }'
```

**Expected Response:**
```json
{
  "taskId": "UPDATE_SAMPLE_STATUS",
  "taskName": "Update Sample Status",
  "extractedEntities": {
    "barcode": "BC123456",
    "sampleStatus": "Ready for - Accessioning"
  },
  "steps": {
    "prechecks": [...],
    "procedure": [...],
    "postchecks": [...]
  }
}
```

---

## Common Edge Cases

| Query | Result | Note |
|-------|--------|------|
| `cancel` | ❌ UNKNOWN | Missing case ID |
| `2025123P6732` | ❌ UNKNOWN | No action verb |
| `cancel status update` | ❌ UNKNOWN | Ambiguous |
| `update sample` | ❌ UNKNOWN | Missing status and barcode |
| `BC123456` | ❌ UNKNOWN | No action verb |
| `2025123P6732 Ready for - Accessioning` | ✅ UPDATE_SAMPLE_STATUS | Has case_id + status (if classified as sample) |
| `cancel case` | ⚠️ CANCEL_CASE | Missing case_id (will show warning) |

---

## Supported Status Values

### Sample Statuses

- `Ready for - Accessioning`
- `Completed - Microtomy`
- `Completed - Microtomy and update workpool`
- `Hold`
- `In Process`
- `Canceled`
- `Not Returned`

### Case Statuses (Legacy)

- `pending`
- `accessioning`
- `grossing`
- `embedding`
- `cutting`
- `staining`
- `microscopy`
- `under_review`
- `on_hold`
- `completed`
- `cancelled`
- `archived`
- `closed`

---

## Pattern Matching Details

### Confidence Scoring

The classifier uses a confidence score based on:
- Number of keyword matches
- Synonym matches
- Entity extraction success
- Minimum confidence threshold (default: 1.0)

**Example:**
- Query: `"cancel case 2025123P6732"`
- Keywords matched: `cancel` (1.0), `case` (1.0)
- Entities extracted: `case_id` (0.5 bonus)
- **Total confidence: 2.5**

### Entity Extraction Priority

1. **First matching pattern wins** - Patterns are evaluated in order
2. **Validation applied** - Extracted values validated against regex/enum
3. **Transform applied** - Optional lowercase/uppercase/trim transforms

**Example:**
```yaml
patterns:
  - "(?:a\\s+|the\\s+)?case\\s+(\\d{4,}\\w+)"  # Tried first
  - "(\\d{7}[A-Z]\\d{4})"  # Fallback if first doesn't match
```

---

## YAML Configuration Examples

### Cancel Case Classification

```yaml
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
  minConfidence: 1.0
```

### Update Sample Status Classification

```yaml
classification:
  keywords:
    - "update sample status"
    - "update slide status"
    - "update container status"
    - "update block status"
  synonyms:
    update: ["change", "modify", "set", "mark"]
    status: ["state"]
    sample: ["slide", "container", "block", "specimen"]
  requiredEntities:
    - barcode
    - sampleStatus
  minConfidence: 1.0
```

---

## Best Practices

### Writing Effective Patterns

✅ **DO:**
- Use capture groups: `(\\d{7}[A-Z]\\d{4})`
- Handle variations: `(?:a\\s+|the\\s+)?`
- Provide multiple patterns for fallback
- Validate extracted values with regex
- Use synonyms for flexibility

❌ **DON'T:**
- Use overly broad patterns: `(.*)`
- Skip validation
- Hard-code specific formats only
- Ignore case variations
- Forget to handle optional words

### Testing Patterns

1. **Test with various formats:**
   - `"cancel case 2025123P6732"`
   - `"please cancel a case 2025123P6732"`
   - `"kindly cancel the case 2025123P6732"`

2. **Test edge cases:**
   - Missing entities
   - Invalid formats
   - Ambiguous queries

3. **Verify extraction:**
   - Check extracted values match expected format
   - Validate against regex patterns
   - Test transform operations

---

## Troubleshooting

### Issue: Classification Returns UNKNOWN

**Check:**
1. Keywords match user query
2. Synonyms are configured correctly
3. Minimum confidence threshold not too high
4. Query contains required keywords

**Solution:**
- Add more keywords/synonyms
- Lower `minConfidence` threshold
- Check query normalization

### Issue: Entity Not Extracted

**Check:**
1. Pattern matches query format
2. Capture group is correct
3. Validation regex allows the value
4. Entity is in `requiredEntities` list

**Solution:**
- Add more patterns
- Adjust regex validation
- Test pattern with regex tester

### Issue: Wrong Classification

**Check:**
1. Multiple runbooks match
2. Confidence scores are similar
3. Keywords overlap between runbooks

**Solution:**
- Make keywords more specific
- Increase `minConfidence` threshold
- Add negative keywords if needed

---

## Summary

✅ **Flexible keyword matching** - Handles variations  
✅ **Robust entity extraction** - Multiple patterns with validation  
✅ **Natural language support** - Handles polite words, articles  
✅ **Status normalization** - Complex formats supported  
✅ **YAML-driven** - Easy to add new patterns  

**Questions?** Check existing runbooks:
- `src/main/resources/runbooks/cancel-case.yaml`
- `src/main/resources/runbooks/update-sample-status.yaml`

---

**Last Updated:** November 14, 2025
