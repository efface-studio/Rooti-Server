# Rooti-Server

Spring Boot 3.3 / Java 21 backend powering the Rooti workforce-management platform.
Replaces the legacy Django service in `RootiBackendNonIgnore/`.

## Architecture

```
src/main/java/com/rooti
├── RootiApplication.java
├── global/                # cross-cutting concerns
│   ├── audit/             # JPA auditing (createdBy/updatedBy)
│   ├── config/            # Web / JPA / Async / Redis / OpenAPI / Firebase
│   ├── exception/         # ErrorCode + GlobalExceptionHandler (RFC 7807)
│   ├── jwt/               # JwtTokenProvider + JwtAuthenticationFilter
│   ├── response/          # ApiResponse, PageResponse
│   ├── security/          # SecurityConfig, PrincipalDetails, @CurrentUser
│   └── util/              # ULID, JSON converters
└── domain/                # package-by-feature, hexagonal-lite
    ├── auth/              # login, refresh, signup, me
    ├── user/              # users table + UserRole enum
    ├── company/           # Company, CompanyCharger
    ├── worker/            # ChallengedWorker, CompanyWorker
    ├── caregiver/         # Caregiver + worker relations
    ├── job/               # JobStandard, JobProcess, JobWorker
    ├── schedule/          # WorkSchedule
    ├── workrecord/        # WorkRecord, WorkProcessRecord
    ├── board/             # Caregiver community board
    ├── document/          # File storage abstraction + PDF generator
    ├── notification/      # FCM push wrapper
    ├── kiosk/             # HiVits Pet kiosk binding
    └── common/            # public/version endpoints
```

Each domain module uses four sub-packages:
* `domain/` – entities, value objects, domain-only behaviour
* `infrastructure/` – Spring Data repositories, QueryDSL queries, external SDK adapters
* `application/` – `@Service` use cases (transactional boundary)
* `presentation/` – REST controllers and DTOs

## Running locally

Requirements: Java 21, Docker (for Postgres + Redis), Gradle wrapper.

```bash
# 1. copy env
cp .env.example .env

# 2. boot dependencies (from repo root)
docker compose up -d postgres redis

# 3. run
./gradlew bootRun
# OR with the IDE: run com.rooti.RootiApplication
```

The first start runs the Flyway migrations in `src/main/resources/db/migration` and
seeds a default admin user (`admin / admin1234`).

### Swagger UI

http://localhost:8080/swagger-ui.html — paste the access token from `POST /api/v1/auth/login` into the "Authorize" dialog.

### Tests

```bash
./gradlew test
```

The integration tests start Postgres via Testcontainers (no extra setup required).

## Key conventions

- **Errors:** every business error throws `BusinessException(ErrorCode.X)` and surfaces as a
  `application/problem+json` body. Frontend pattern-matches on `code` (the `ErrorCode.name()`).
- **Responses:** success bodies always carry an `ApiResponse<T>` envelope. Pagination uses
  `PageResponse<T>` — never the raw Spring `Page`.
- **Auth:** stateless JWT only. Access tokens live 15 minutes; refresh tokens live 14 days and
  are stored in Redis so they can be revoked.
- **JSONB fields:** the legacy "context" Map columns are kept as `Map<String,Object>` via
  `JsonAttributeConverter` to keep the data lossless during the migration.
- **Time:** the JVM TZ is pinned to `Asia/Seoul`; all `LocalDateTime` columns are wall-clock KST.

## What's intentionally different vs the Django app

| Concern                | Django (old)                       | Spring (new)                              |
|------------------------|-------------------------------------|-------------------------------------------|
| Sessions + JWT mixed   | both                                | JWT only — no server session              |
| Refresh tokens         | client-only                         | Redis-backed, revocable                   |
| Validation             | DRF serializers                     | `@Valid` + Bean Validation                |
| Error format           | inconsistent                        | RFC 7807 `application/problem+json`       |
| Pagination payload     | leaks `Pageable`                    | flat `PageResponse<T>`                    |
| HTML sanitization      | not enforced                        | JSoup whitelist before persistence        |
| PDF                    | WeasyPrint (Python+native deps)     | openhtmltopdf (pure Java)                 |
| Schema management      | Django migrations                   | Flyway (raw SQL, reviewable)              |
| FCM lifecycle          | sync                                | `@Async` + caller-runs back-pressure      |
| Time                   | KST via Django settings             | JVM-pinned KST + `Asia/Seoul` columns     |
