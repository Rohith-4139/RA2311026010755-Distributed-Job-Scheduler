# Distributed Job Scheduler

This repository implements a production-inspired Distributed Job Scheduler.

Quick start (development):

- Start PostgreSQL (docker-compose is provided):

```bash
docker-compose up -d
```

- Run backend (Spring Boot):

```powershell
cd backend
..\maven\apache-maven-3.9.6\bin\mvn spring-boot:run
```

- Run frontend (Vite):

```powershell
cd frontend
npm install
npm run dev -- --host 0.0.0.0 --port 3000
```

- Run tests:

```bash
cd backend
..\maven\apache-maven-3.9.6\bin\mvn test
```

What you'll find here
- `backend/` — Spring Boot API, data model, worker, tests
- `frontend/` — React + Vite dashboard
- Docs: `ARCHITECTURE.md`, `ER_DIAGRAM.md`, `API.md`, `DESIGN_DECISIONS.md`

Notes
- Worker claim uses `SELECT ... FOR UPDATE SKIP LOCKED` to guarantee atomic claims under concurrency.
- The DLQ AI summarizer is pluggable and degrades to a static summary when no API key is configured.


A production-inspired full-stack distributed job scheduler with Spring Boot, PostgreSQL, JWT auth, atomic job claiming, retry/DLQ lifecycle, worker heartbeats, and a React dashboard.

## Features

- JWT authentication and project/queue CRUD (priority, concurrency limit, pause/resume)
- Job submission: immediate, delayed (`delaySeconds`), cron (`cronExpression`), batch insert
- Atomic claiming via PostgreSQL `SELECT … FOR UPDATE SKIP LOCKED`
- Pluggable job handler (sleep simulation, forced failure, HTTP webhook)
- Retry policies: fixed, linear, exponential backoff → DLQ escalation
- Worker heartbeats with watchdog recovery for stale workers
- React dashboard: queue health, job explorer, worker grid, throughput chart, DLQ with retry
- Optional AI failure summaries (OpenAI-compatible, degrades gracefully without API key)

## Prerequisites

- Java 17+
- Node.js 20+
- Docker Desktop (recommended for PostgreSQL)

## Quick start

### 1. Start PostgreSQL

```bash
docker compose up -d postgres
```

Database: `distributed_scheduler` · User: `postgres` · Password: `postgres` · Port: `5432`

### 2. Start backend (includes worker)

```bash
cd backend
../maven/apache-maven-3.9.6/bin/mvn spring-boot:run
```

API runs at `http://localhost:8080`.

To run API-only (no worker):

```bash
SCHEDULER_WORKER_ENABLED=false ../maven/apache-maven-3.9.6/bin/mvn spring-boot:run
```

### 3. Start frontend

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:3000`, register a user, and explore the dashboard.

### 4. Optional: AI summaries

```bash
export LLM_API_KEY=sk-...
export LLM_BASE_URL=https://api.openai.com/v1
```

## Run tests

Requires PostgreSQL running on `localhost:5432`.

```bash
cd backend
../maven/apache-maven-3.9.6/bin/mvn test
```

Integration tests cover:

- **ConcurrentClaimTest** — 5 workers claim concurrently; no duplicate claims; concurrency limit enforced
- **RetryEscalationTest** — failed job retries then escalates to DLQ
- **ApiContractTest** — JWT auth and validation error shape

## Project structure

```
backend/          Spring Boot API + worker + schedulers
frontend/         React (Vite) + Tailwind + Recharts dashboard
docker-compose.yml
ARCHITECTURE.md   System design and claim flow
ER_DIAGRAM.md     Database schema (11 tables)
API.md            Full REST API reference
DESIGN_DECISIONS.md
```

## Demo job payloads

| Payload | Result |
|---|---|
| `sleep:1000` | 1s simulated work, succeeds |
| `fail-simulation` | Fails, retries, then DLQ |
| `https://httpbin.org/post` | HTTP POST webhook |

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — services, claim flow, lifecycle
- [ER_DIAGRAM.md](ER_DIAGRAM.md) — entity relationships and indexes
- [API.md](API.md) — every endpoint with request/response shapes
- [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md) — claiming strategy, retries, deferred scope
