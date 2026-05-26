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

V2 Spring Boot 서버는 **순수 Spring(V2) 스키마**만 사용합니다. Django 호환 매핑 레이어는 의도적으로 제공하지 않습니다.

운영 RDS 인스턴스에는 이미 Django v1 시절의 데이터가 있을 수 있으므로, 한 번만 실행하는 **데이터 마이그레이션 스크립트**를 제공합니다. 자세한 절차는 [`legacy-migration/README.md`](./legacy-migration/README.md) 참고.

### 절차 (cutover)

```bash
# 1) RDS 스냅샷 (롤백 대비)

# 2) bastion / VPN 서버에서 SSL 강제로 접속
psql "postgresql://rooti_admin@<rds-endpoint>:5432/rooti?sslmode=require"

# 3) V2 스키마 생성 (Django 테이블 옆에 같이 살게 됨)
\i src/main/resources/db/migration/V1__init_schema.sql
\i src/main/resources/db/migration/V2__seed_default_data.sql

# 4) Django 데이터 → V2 테이블 이관 (idempotent)
\i legacy-migration/V1-django-to-spring.sql

# 5) 검증
\i legacy-migration/V2-verify.sql      # 모든 src=dst 확인

# 6) V2 앱 배포 후 영업일 1일 모니터링

# 7) 더 이상 롤백 불필요하면 Django 테이블 삭제
\i legacy-migration/V3-drop-django-tables.sql
```

이후 Spring 앱을 실행:

```bash
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

### TLS / SSL

`DB_SSL_REQUIRED=true` 를 주면 `DataSourceUrlPostProcessor` 가 JDBC URL에 `sslmode=require` 를 자동으로 부착합니다. RDS 운영에서는 항상 켭니다. 동시에 RDS 파라미터 그룹의 `rds.force_ssl=1` 도 설정하세요.

### 점검 체크리스트

1. RDS 보안 그룹 인바운드: ECS / EC2 보안 그룹에서 5432 허용
2. RDS 파라미터 그룹: `rds.force_ssl=1`
3. 앱용 DB 사용자(`rooti_app`)는 V2 테이블에 한해 `SELECT/INSERT/UPDATE/DELETE` 만 부여 (DDL은 별도 admin 유저)
4. ElastiCache(Redis) 동일 VPC 배치, AUTH token 사용 시 `REDIS_PASSWORD` 환경변수
5. CORS 도메인은 운영 도메인으로 잠금 (`CORS_ALLOWED_ORIGINS`)
6. 마이그레이션 직후 `legacy-migration/V2-verify.sql` 결과를 캡쳐해서 저장 (감사 추적)
