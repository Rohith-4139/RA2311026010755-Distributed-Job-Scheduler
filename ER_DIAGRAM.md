# Entity Relationship Diagram

All foreign keys are indexed. The hot claim path uses composite index `(queue_id, status, scheduled_at)` on `jobs`.

Cascade deletes: `Project → Queue → Job`. Job executions, logs, and DLQ entries are retained when a job row is removed (`ON DELETE SET NULL` on execution FKs).

```mermaid
erDiagram
    USERS ||--o{ ORGANIZATIONS : owns
    ORGANIZATIONS ||--o{ PROJECTS : contains
    PROJECTS ||--o{ QUEUES : contains
    QUEUES ||--o{ JOBS : contains
    QUEUES ||--o{ RETRY_POLICIES : configures
    QUEUES ||--o{ SCHEDULED_JOBS : owns
    JOBS ||--o{ JOB_EXECUTIONS : has
    JOBS ||--o{ DEAD_LETTER_QUEUE_ENTRIES : escalates
    JOB_EXECUTIONS ||--o{ JOB_LOGS : has
    WORKERS ||--o{ WORKER_HEARTBEATS : heartbeat

    USERS {
        bigint id PK
        string username UK
        string password
        string role
        datetime created_at
    }

    ORGANIZATIONS {
        bigint id PK
        string name
        datetime created_at
    }

    PROJECTS {
        bigint id PK
        bigint organization_id FK
        string name
        datetime created_at
    }

    QUEUES {
        bigint id PK
        bigint project_id FK
        string name
        int priority
        int concurrency_limit
        boolean paused
        datetime created_at
    }

    JOBS {
        bigint id PK
        bigint queue_id FK
        string payload
        enum status
        int priority
        int max_retries
        int retry_count
        datetime scheduled_at
        datetime created_at
        datetime completed_at
        int version
    }

    JOB_EXECUTIONS {
        bigint id PK
        bigint job_id FK
        string worker_id
        string status
        datetime started_at
        datetime completed_at
        text log_summary
        text error_message
    }

    RETRY_POLICIES {
        bigint id PK
        bigint queue_id FK
        enum type
        int delay_seconds
        double backoff_multiplier
        int max_retries
    }

    WORKERS {
        string id PK
        string name
        string status
        datetime registered_at
    }

    WORKER_HEARTBEATS {
        bigint id PK
        string worker_id FK
        datetime last_ping
    }

    JOB_LOGS {
        bigint id PK
        bigint job_execution_id FK
        text log_content
        datetime created_at
    }

    SCHEDULED_JOBS {
        bigint id PK
        bigint queue_id FK
        string cron_expression
        string payload
        datetime next_run_at
        datetime last_run_at
        boolean active
    }

    DEAD_LETTER_QUEUE_ENTRIES {
        bigint id PK
        bigint job_id FK
        text error_message
        text ai_summary
        datetime failed_at
    }
```

## Job status enum

`QUEUED`, `SCHEDULED`, `CLAIMED`, `RUNNING`, `COMPLETED`, `FAILED`, `DEAD_LETTER`

## Index summary

| Table | Index | Purpose |
|---|---|---|
| jobs | `(queue_id, status, scheduled_at)` | Atomic claim query |
| jobs | `status` | Dashboard filters |
| queues | `project_id` | Project listing |
| job_executions | `job_id`, `worker_id` | Execution lookup, watchdog |
| worker_heartbeats | `worker_id` | Heartbeat lookup |
| dead_letter_queue_entries | `job_id` | DLQ retry |
