# Rooti-Server

**Python 3.12 / FastAPI** backend powering the Rooti workforce-management platform.
Migrated from the previous Spring Boot 3.3 / Java 21 implementation while keeping
the same PostgreSQL schema, Flyway migrations, and HTTP API contract — the
React client (`Rooti-Client/`) needs no changes.

## Architecture (FastAPI-native, flat by concern)

```
app/
├── main.py              # FastAPI app factory + lifespan
├── core/                # Cross-cutting (Spring `global/` 등가)
│   ├── config.py        # pydantic-settings — reads SPRING_PROFILES_ACTIVE, DB_*, JWT_*, …
│   ├── database.py      # SQLAlchemy 2.x async engine + session dependency
│   ├── redis.py         # async Redis client
│   ├── security.py      # JWT (HS256, Java-compatible claims) + bcrypt + current_user
│   ├── exceptions.py    # ErrorCode (33 codes) + BusinessException + RFC 7807 handlers
│   ├── response.py      # ApiResponse / PageResponse — same JSON shape as the Java app
│   ├── pagination.py    # Spring Pageable 등가 (?page=&size=)
│   ├── router.py        # RootiRouter (NON_NULL + camelCase alias defaults)
│   ├── logging.py       # structlog (JSON in prod)
│   └── time.py          # Asia/Seoul helpers
├── models/              # SQLAlchemy ORM (one Base.metadata for all tables)
│   ├── base.py
│   ├── enums.py
│   ├── user.py · company.py · worker.py · caregiver.py
│   ├── job.py · work.py · document.py · board.py · kiosk.py
├── schemas/             # Pydantic DTOs (per-resource)
├── services/            # Business logic (per-resource)
├── api/
│   ├── deps.py          # Shared deps + service factories + role guards
│   ├── health.py        # /actuator/health, /info, /prometheus
│   └── v1/              # /api/v1/* — one file per resource
│       ├── public.py  auth.py  companies.py  workers.py  caregivers.py
│       ├── kiosks.py  job_standards.py  job_workers.py
│       ├── schedules.py  work_records.py  boards.py
│       ├── notifications.py  documents.py  work_journals.py
└── integrations/        # External services
    ├── firebase.py      # FCM (firebase-admin)
    ├── storage.py       # File storage (local disk; S3 next)
    ├── pdf.py           # WeasyPrint (PDF rendering)
    └── xlsx.py          # openpyxl (XLSX rendering)
```

DB schema lives in `migrations/V*.sql` (still applied by **Flyway** — `make server-migrate`).

## Setup

Requires Python 3.12 and [Poetry](https://python-poetry.org/).

```bash
make server-install        # poetry install (creates .venv + deps)
make up-infra              # docker compose up -d postgres redis
cp .env.dev.example .env.dev   # fill in secrets
make server-migrate        # apply Flyway migrations (Docker image)
make server                # uvicorn on :8080 (--reload)

open http://localhost:8080/swagger-ui.html
```

Environment variables are the **same** as the Java app — `SPRING_PROFILES_ACTIVE`,
`DB_HOST`, `DB_PASSWORD`, `REDIS_HOST`, `JWT_SECRET`, `FIREBASE_CREDENTIAL_JSON_PATH`,
`RESEND_API_KEY`, `STORAGE_*`, `CORS_ALLOWED_ORIGINS`, …

## Tests

```bash
make server-test           # pytest (unit + OpenAPI route lock)
```

The route-lock test (`tests/test_openapi_routes.py`) guarantees every endpoint
the React client expects is still mounted — regression-proofing the migration.

## API compatibility with the Java server

| Concern | Status |
| --- | --- |
| URL paths | `/api/v1/**`, `/actuator/health`, `/v3/api-docs`, `/swagger-ui.html` — unchanged |
| Response envelope | `ApiResponse` keeps `success/data/timestamp` with `+09:00`; `null` data omitted |
| `PageResponse` | Same camelCase keys (`totalElements`, `hasNext`, …) |
| Error responses | RFC 7807 `application/problem+json`, identical `code`/`status`/`detail` |
| JWT tokens | HS256, issuer `rooti`, claims `sub/usn/roles/typ` — Java tokens cross-validate |
| BCrypt hashes | Cost 10 — Spring's `BCryptPasswordEncoder` and Python's `bcrypt` interop |

## What's intentionally deferred

A few corners of the document/job domain are stubbed for follow-up PRs:

- **HWP rendering** (한글워드프로세서) — placeholder bytes, same as Java's note
- **Bulk-email journal pipeline** (render → ZIP → Resend) — 501 from `/api/v1/work-journals/bulk-email`
- **S3 storage driver** — only local disk implemented; interface ready
- **Spring `@Cacheable` Redis caching** for company / job-standard reads
- **`X-Trace-Id` MDC middleware** (Java had `traceId` log marker)

See task TODOs in [app/services/document.py](app/services/document.py) and
[app/services/auth.py](app/services/auth.py).
