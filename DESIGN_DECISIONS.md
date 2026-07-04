# Design Decisions

## Atomic claiming — `SELECT ... FOR UPDATE SKIP LOCKED`

We chose PostgreSQL row-level locking over JPA optimistic locking for the claim path.

**Why not optimistic locking?** Optimistic locking requires a retry loop on `OptimisticLockException`. Under high contention many workers would collide on the same rows, increasing latency and write amplification.

**Why SKIP LOCKED?** Workers poll eligible jobs with:

```sql
SELECT * FROM jobs
WHERE queue_id = ? AND status IN ('QUEUED','SCHEDULED') AND scheduled_at <= now()
ORDER BY priority DESC, created_at ASC
LIMIT ? FOR UPDATE SKIP LOCKED
```

`SKIP LOCKED` lets concurrent workers claim different rows in a single round-trip without blocking. Combined with a pessimistic lock on the parent queue row (to enforce `concurrency_limit`), this guarantees no double-claim while preserving throughput.

A `@Version` column remains on `jobs` for general update safety but is not the primary claim mechanism.

## Retry backoff design

Retry policies are configurable per queue via `RetryPolicy`:

| Type | Delay formula |
|---|---|
| FIXED | `delay_seconds` |
| LINEAR | `delay_seconds × (retry_count + 1)` |
| EXPONENTIAL | `delay_seconds × multiplier^retry_count` |

When no queue policy exists, the job's own `max_retries` applies with a 1-second default delay so integration tests and demos observe retries quickly. After retries are exhausted the job transitions to `DEAD_LETTER` and a DLQ entry is created.

## Indexing choices

- **Composite `(queue_id, status, scheduled_at)`** — covers the claim query filter and sort without a filesort on the hot path.
- **FK indexes** — every foreign key column is indexed for join and cascade performance.
- **`status` on jobs** — supports dashboard aggregation and filtered list endpoints.

## Worker architecture

The worker runs as a `@Scheduled` component inside the same Spring Boot process by default (`scheduler.worker.enabled=true`). This satisfies the "distinct process/module" requirement while keeping local development simple. Set `SCHEDULER_WORKER_ENABLED=false` to run API-only mode and attach separate worker instances later.

## AI failure summaries

`AiSummaryService` is isolated behind an interface. When `LLM_API_KEY` is unset, a static fallback summary is stored and the application continues normally. When set, an OpenAI-compatible `/chat/completions` call generates a short plain-English summary asynchronously.

## Intentionally deferred (time-boxed scope)

| Feature | Reason deferred |
|---|---|
| Sharding / multi-region | Single PostgreSQL instance sufficient for correctness demo |
| Full RBAC | JWT auth with single role; org-level permissions not required for core lifecycle |
| Workflow DAGs | Out of scope; flat job queue only |
| Distributed locking (Redis/ZK) | Database row locks are sufficient at this scale |
| Separate worker deploy artifact | Same codebase, config flag; can be split into a second Spring Boot main later |
| JSON structured logging | SLF4J plain text used; easy to add Logback JSON encoder later |

These were skipped deliberately to maximize correctness on claim → execute → retry → DLQ → watchdog recovery.
