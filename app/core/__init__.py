"""Cross-cutting infrastructure (equivalent of `com.rooti.global`).

- `config`     — pydantic-settings, reads the same env vars as Spring Boot
- `database`   — SQLAlchemy 2.x async engine + session dependency
- `redis`      — async Redis client
- `security`   — JWT encode/decode, password hashing
- `response`   — ApiResponse / PageResponse mirroring the Java JSON shape
- `exceptions` — RFC 7807 ProblemDetail handlers (matches Spring's @ExceptionHandler)
- `logging`    — structlog setup (JSON in prod, pretty in dev)
- `router`     — RootiRouter subclass that defaults `response_model_exclude_none=True`
- `time`       — KST helpers (Asia/Seoul, matches Jackson time-zone setting)
"""
