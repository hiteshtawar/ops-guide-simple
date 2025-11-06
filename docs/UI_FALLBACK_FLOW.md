# UI Fallback Flow - Manual Task Selection

## Overview

When automatic pattern detection returns `UNKNOWN`, the UI can show available tasks for manual selection.

---

## User Flow

### Happy Path (Automatic Detection)

```
User: "cancel case 2025123P6732"
    ↓
Classification: CANCEL_CASE
    ↓
Show runbook steps
```

### Fallback Path (Manual Selection)

```
User: "do something with case 2025123P6732"
    ↓
Classification: UNKNOWN
    ↓
UI: "I couldn't understand that. Did you mean:"
    ☐ Cancel Case
    ☐ Update Case Status
    ↓
User selects: "Cancel Case"
    ↓
Resubmit with: { query: "do something with case 2025123P6732", taskId: "CANCEL_CASE" }
    ↓
Entity extraction: case_id = "2025123P6732"
    ↓
Show runbook steps
```

---

## API Endpoints

### 1. Get Available Tasks

**GET** `/api/v1/tasks`

**Response:**
```json
[
  {
    "taskId": "CANCEL_CASE",
    "taskName": "Cancel Case",
    "description": "Cancel a pathology case. Type: cancel case 2025P1234 and hit Send"
  },
  {
    "taskId": "UPDATE_CASE_STATUS",
    "taskName": "Update Case Status",
    "description": "Update case workflow status. Type: update case status to pending 2025P1234 and hit Send"
  }
]
```

### 2. Process with Manual Task Selection

**POST** `/api/v1/process`

**Request (with explicit taskId):**
```json
{
  "query": "do something with case 2025123P6732",
  "userId": "engineer@example.com",
  "taskId": "CANCEL_CASE"
}
```

**Behavior:**
- ✅ Classification skipped (uses provided taskId)
- ✅ Entity extraction still runs (extracts case_id from query)
- ✅ Returns runbook for CANCEL_CASE with extracted entities

**Response:**
```json
{
  "taskId": "CANCEL_CASE",
  "taskName": "Cancel Case",
  "extractedEntities": {
    "case_id": "2025123P6732"
  },
  "steps": [...],
  "warnings": [...]
}
```

---

## UI Implementation

### React Example

```javascript
const [availableTasks, setAvailableTasks] = useState([])
const [showTaskSelector, setShowTaskSelector] = useState(false)

// Fetch available tasks on mount
useEffect(() => {
  fetchAvailableTasks()
}, [])

const fetchAvailableTasks = async () => {
  const response = await fetch('http://localhost:8093/api/v1/tasks', {
    headers: {
      'Authorization': `Bearer ${JWT_TOKEN}`
    }
  })
  const tasks = await response.json()
  setAvailableTasks(tasks)
}

// Handle query submission
const handleSubmit = async () => {
  const response = await processQuery(query)
  
  if (response.taskId === 'UNKNOWN') {
    // Show task selector
    setShowTaskSelector(true)
  } else {
    // Show runbook
    displayRunbook(response)
  }
}

// Handle manual task selection
const handleTaskSelection = async (taskId) => {
  const response = await processQuery(query, taskId)
  setShowTaskSelector(false)
  displayRunbook(response)
}

const processQuery = async (query, taskId = null) => {
  const body = { query, userId: 'user@example.com' }
  if (taskId) {
    body.taskId = taskId
  }
  
  const response = await fetch('http://localhost:8093/api/v1/process', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${JWT_TOKEN}`
    },
    body: JSON.stringify(body)
  })
  
  return response.json()
}
```

### UI Mockup

```
┌────────────────────────────────────────┐
│  Query: "do something with 2025P6732"  │
│  [Send]                                │
└────────────────────────────────────────┘

↓ (Classification returns UNKNOWN)

┌────────────────────────────────────────┐
│  ⚠️ I couldn't understand that.        │
│                                        │
│  What would you like to do?            │
│                                        │
│  ○ Cancel Case                         │
│    Type: cancel case 2025P1234         │
│                                        │
│  ○ Update Case Status                  │
│    Type: update status to pending      │
│           2025P1234                    │
│                                        │
│  [Show Example] [Dismiss]              │
└────────────────────────────────────────┘

↓ (User selects "Cancel Case")

┌────────────────────────────────────────┐
│  Task: Cancel Case                     │
│  Case ID: 2025P6732 ✓                  │
│                                        │
│  Steps:                                │
│  1. Verify case exists       [Run]     │
│  2. Check not cancelled      [Run]     │
│  3. Cancel case        [Approve & Run] │
│  4. Verify updated           [Run]     │
└────────────────────────────────────────┘
```

---

## Key Features

### 1. Automatic Detection First
Always try automatic pattern detection first for best UX.

### 2. Smart Fallback
Only show task selector when classification is UNKNOWN.

### 3. Entity Extraction Always Runs
Even with manual task selection, entities are extracted from the query:
- Case IDs
- Status values
- Other parameters

### 4. Preserves Original Query
When manually selecting a task, keep the original query for entity extraction.

---

## Benefits

✅ **Better UX** - Users can proceed even if pattern detection fails  
✅ **Faster** - Skip typing new query, just select task type  
✅ **Discoverable** - Users see what operations are available  
✅ **Flexible** - Supports both natural language and manual selection  

---

## Example Queries That Need Fallback

| Query | Auto-Detection | Fallback |
|-------|---------------|----------|
| "do something with case 123" | UNKNOWN ❌ | Show selector ✅ |
| "help with 2025P6732" | UNKNOWN ❌ | Show selector ✅ |
| "case 123 needs attention" | UNKNOWN ❌ | Show selector ✅ |
| "cancel case 123" | CANCEL_CASE ✅ | Not needed |
| "update status to pending 123" | UPDATE_CASE_STATUS ✅ | Not needed |

---

**Last Updated:** November 6, 2025

