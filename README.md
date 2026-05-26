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

---

## AWS RDS 연결 (운영)

V2 Spring Boot 서버는 새 스키마(`users`, `companies`, ...)로 만들어졌지만, 운영 RDS에는 이미 Django v1 시절의 테이블(`auth_user`, `works_jobstandard`, `care_caregiver` 등)이 자리 잡고 있습니다. 두 가지 모드를 지원합니다.

### Mode A: Fresh schema (권장, 신규 RDS 인스턴스)

V2 스키마를 그대로 적용. Flyway가 V1/V2 마이그레이션을 실행합니다.

```bash
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

### Mode B: Legacy compatibility (기존 v1 RDS에 그대로 연결)

기존 Django 테이블을 그대로 두고 V2 코드가 그 테이블에 매핑되어 동작하도록 합니다.

```bash
SPRING_PROFILES_ACTIVE=prod,legacy \
  ROOTI_LEGACY_SCHEMA=true \
  FLYWAY_ENABLED=false \
  HIBERNATE_DDL_AUTO=none \
  ./gradlew bootRun
```

핵심 메커니즘:
- `LegacySchemaNamingStrategy` 가 `users` → `auth_user`, `job_standards` → `works_jobstandard` … 처럼 V2 → v1 테이블 이름을 자동 변환
- Hibernate `ddl-auto=none`, Flyway 비활성화 — 운영 DB 스키마는 절대 손대지 않음
- 컬럼명이 다른 케이스는 엔티티 단에서 `@Column(name = ...)` 으로 명시 (점진적으로 보강)

### TLS / SSL

`DB_SSL_REQUIRED=true` 를 주면 `DataSourceUrlPostProcessor` 가 JDBC URL에 `sslmode=require` 를 자동으로 붙입니다. RDS 운영 환경에서는 항상 켭니다.

### 점검 체크리스트

1. RDS 보안 그룹 인바운드: ECS / EC2 보안 그룹에서 5432 허용
2. RDS 파라미터 그룹: `rds.force_ssl=1`
3. DB 사용자(`rooti_app`)는 `SELECT/INSERT/UPDATE/DELETE` 만, DDL 권한 없음
4. ElastiCache(Redis) 동일 VPC 배치, AUTH token 사용 시 `REDIS_PASSWORD` 환경변수
5. CORS 도메인은 운영 도메인으로 잠금 (`CORS_ALLOWED_ORIGINS`)
6. 첫 배포 전 read-only(`SELECT`) 권한 유저로 접속해서 `SELECT 1 FROM auth_user LIMIT 1` 으로 매핑 확인
