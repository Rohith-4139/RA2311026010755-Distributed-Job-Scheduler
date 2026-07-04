# API Reference

Base URL: `http://localhost:8080`  
Version prefix: `/api/v1`  
Auth: Bearer JWT on all endpoints except `/api/v1/auth/**`

## Error response shape

All errors return:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed: payload: Job payload cannot be blank",
  "timestamp": "2026-07-04T23:00:00",
  "path": "/api/v1/jobs/queue/1"
}
```

Codes: `VALIDATION_ERROR`, `BAD_REQUEST`, `INTERNAL_SERVER_ERROR`

---

## Authentication

### POST `/api/v1/auth/register`

Register a new user. **No auth required.**

**Request:**
```json
{ "username": "alice", "password": "secret123" }
```

**Response:** `200` — `"User registered successfully"`

### POST `/api/v1/auth/login`

**Request:**
```json
{ "username": "alice", "password": "secret123" }
```

**Response:**
```json
{ "token": "<jwt>", "username": "alice" }
```

---

## Projects

### GET `/api/v1/projects`

List all projects. **Auth required.**

**Response:** `[{ "id": 1, "name": "Default Project", "createdAt": "..." }]`

### POST `/api/v1/projects`

**Request:** `{ "name": "My Project" }`  
**Response:** Project object

### GET `/api/v1/projects/default`

Returns or creates the default project. **Auth required.**

### DELETE `/api/v1/projects/{id}`

Cascade-deletes queues and jobs under the project.

---

## Queues

### GET `/api/v1/queues`

List all queues.

### GET `/api/v1/queues/project/{projectId}`

Queues for a specific project.

### POST `/api/v1/queues`

**Request:**
```json
{
  "name": "high-priority",
  "projectId": 1,
  "priority": 10,
  "concurrencyLimit": 5,
  "paused": false
}
```

### PUT `/api/v1/queues/{id}`

Partial update with same fields as create.

### POST `/api/v1/queues/{id}/pause`

Sets `paused=true`.

### POST `/api/v1/queues/{id}/resume`

Sets `paused=false`.

### DELETE `/api/v1/queues/{id}`

Deletes queue and cascaded jobs.

---

## Jobs

### POST `/api/v1/jobs/queue/{queueId}`

Create a single job (immediate, delayed, or cron).

**Request:**
```json
{
  "payload": "sleep:1000",
  "priority": 1,
  "maxRetries": 3,
  "delaySeconds": 0,
  "cronExpression": null
}
```

- **Immediate:** omit `delaySeconds` and `cronExpression`
- **Delayed:** set `delaySeconds` > 0 → status `SCHEDULED`
- **Cron:** set `cronExpression` → creates a `ScheduledJob` template

**Response:** Job or ScheduledJob object

### POST `/api/v1/jobs/queue/{queueId}/batch`

**Request:** array of JobRequest objects  
**Response:** array of Job objects

### GET `/api/v1/jobs`

Paginated job explorer with filters.

| Param | Type | Description |
|---|---|---|
| queueId | long | Filter by queue |
| status | enum | QUEUED, RUNNING, etc. |
| start | ISO datetime | Created after |
| end | ISO datetime | Created before |
| page | int | Default 0 |
| size | int | Default 10 |

**Response:** Spring Page `{ content: [...], totalPages, totalElements, ... }`

### GET `/api/v1/jobs/dlq`

Paginated dead letter queue entries.

**Response:** Page of `{ id, job, errorMessage, aiSummary, failedAt }`

### POST `/api/v1/jobs/dlq/{dlqEntryId}/retry`

Requeues the original job and removes the DLQ entry.

### GET `/api/v1/jobs/metrics`

**Response:**
```json
{
  "statusCounts": { "QUEUED": 5, "RUNNING": 2, "COMPLETED": 100 },
  "queueCount": 3,
  "workerCount": 1,
  "activeWorkers": 1
}
```

---

## Workers

### GET `/api/v1/workers`

List registered workers.

### GET `/api/v1/workers/status`

Workers with heartbeat and active execution counts.

**Response:**
```json
[{
  "id": "uuid",
  "name": "Worker-abc12345",
  "status": "ACTIVE",
  "registeredAt": "...",
  "lastPing": "...",
  "activeExecutions": 2
}]
```

---

## Dashboard

### GET `/api/v1/dashboard/queues/health`

Per-queue health with status counts.

**Response:**
```json
[{
  "id": 1,
  "name": "default",
  "priority": 1,
  "concurrencyLimit": 5,
  "paused": false,
  "projectId": 1,
  "statusCounts": { "QUEUED": 3, "RUNNING": 1 },
  "runningCount": 1,
  "health": "HEALTHY"
}]
```

Health values: `HEALTHY`, `PAUSED`, `BACKLOG`, `AT_CAPACITY`

### GET `/api/v1/dashboard/throughput?hours=24`

Hourly completed job counts for charting.

**Response:**
```json
[{ "hour": "2026-07-04T10:00", "completed": 12 }]
```

---

## Job payload conventions (demo handler)

| Payload | Behavior |
|---|---|
| `sleep:2000` | Sleep 2 seconds then succeed |
| `fail` or `fail-simulation` | Simulated failure (triggers retry/DLQ) |
| `https://...` | HTTP POST webhook |

---

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `LLM_API_KEY` | (empty) | OpenAI-compatible API key for DLQ summaries |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | LLM endpoint |
| `LLM_MODEL` | `gpt-4o-mini` | Model name |
| `SCHEDULER_WORKER_ENABLED` | `true` | Enable in-process worker |
| `SCHEDULER_WORKER_POLL_INTERVAL_MS` | `3000` | Worker poll interval |
