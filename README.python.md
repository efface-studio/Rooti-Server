# Rooti-Server — Python / FastAPI (마이그레이션 진행 중)

> 기존 Spring Boot 3.3 / Java 21 서버를 **Python 3.12 + FastAPI** 로 점진적으로 치환하는 작업의 작업 공간입니다.
> Java 코드 (`src/`, `build.gradle.kts`, `gradle/`) 는 한 도메인이 Python 으로 옮겨질 때마다 같이 삭제됩니다.
> 그 전까지는 두 런타임이 **같은 PostgreSQL DB / 같은 .env 파일** 을 공유합니다.

---

## 1. 디렉토리 구조

```
Rooti-Server/
├── app/                          # ← Python (FastAPI)
│   ├── main.py                   #   app factory, lifespan, CORS, OpenAPI
│   ├── core/                     #   = com.rooti.global
│   │   ├── config.py             #     pydantic-settings (env 로 같은 .env 읽음)
│   │   ├── database.py           #     SQLAlchemy 2.x async + asyncpg
│   │   ├── redis.py              #     redis.asyncio
│   │   ├── security.py           #     JWT (HS256) + bcrypt
│   │   ├── response.py           #     ApiResponse / PageResponse (Java 와 JSON 동일)
│   │   ├── exceptions.py         #     RFC 7807 ProblemDetail 핸들러
│   │   ├── logging.py            #     structlog (JSON in prod)
│   │   ├── router.py             #     RootiRouter (response_model_exclude_none 기본값)
│   │   └── time.py               #     KST 헬퍼
│   ├── api/
│   │   ├── health.py             #     /actuator/health, /actuator/info
│   │   └── v1/__init__.py        #     /api/v1 마운트 포인트
│   └── domain/                   #   = com.rooti.domain
│       └── (도메인은 하나씩 추가)
├── tests/                        # pytest + httpx + (필요 시) testcontainers
├── pyproject.toml                # 의존성 / 빌드 / pytest / mypy
├── ruff.toml                     # lint + format 규칙
├── .python-version               # 3.12
│
├── src/                          # ← Java (Spring Boot) — 도메인 옮겨질 때 함께 삭제
├── build.gradle.kts              # ← 위와 동일
└── ...
```

---

## 2. 셋업

권장: **[uv](https://docs.astral.sh/uv/)** (Astral). pip / poetry 도 동일하게 동작.

```bash
# 의존성 설치 (.venv 자동 생성)
make server-py-install
# == cd Rooti-Server && uv venv && uv pip install -e ".[dev]"

# 실행 (postgres/redis 는 make up-infra 로 띄워둘 것)
make server-py
# == uv run uvicorn app.main:app --reload --port 8080

# 테스트
make server-py-test

# 포맷 + lint --fix
make server-py-fmt
```

환경변수는 **Java 와 동일한 `.env.dev` / `.env.prod` 를 그대로 재사용** 합니다.
`SPRING_PROFILES_ACTIVE` → `app_env` 로 매핑되며, `dev|prod|local|test` 중 하나여야 합니다.

---

## 3. Java 와의 호환 보장

| 측면 | 결정 | 비고 |
| --- | --- | --- |
| DB 스키마 | **Flyway 유지** — `src/main/resources/db/migration/V*.sql` 그대로 사용 | Java 가 죽기 전까지는 Java 가 마이그레이션 적용, 향후 Flyway CLI(Docker) 로 분리 |
| API 응답 envelope | `ApiResponse` / `PageResponse` 의 JSON 키·타임존(+09:00)·NON_NULL 규칙 1:1 동일 | `tests/test_response_envelope.py` 가 잠금 |
| 에러 응답 | RFC 7807 `application/problem+json` | Spring 의 ProblemDetail 등가 |
| JWT | HS256, issuer `rooti`, 같은 secret/TTL | Java 가 발급한 토큰을 Python 이 그대로 검증 가능 (이행기 무중단) |
| URL | `/api/v1/**`, `/actuator/health`, `/v3/api-docs`, `/swagger-ui.html` | 클라이언트/모니터링 무수정 |
| 타임존 | `Asia/Seoul` | `app/core/time.py:KST` |

---

## 4. 도메인 마이그레이션 순서 (제안)

의존성이 적은 것부터, 트래픽 클리티컬 라인은 마지막.

| # | 도메인 | 사유 |
| --- | --- | --- |
| 1 | `common` | 공통 enum / 값 객체. 모든 도메인이 import. |
| 2 | `user` + `auth` | 로그인 / 토큰 발급. 나머지 도메인의 인증 기반. |
| 3 | `company` | 조직 단위. user 와 N:M. |
| 4 | `caregiver` + `worker` | 인사 정보. |
| 5 | `kiosk` | 출퇴근 디바이스. caregiver 의존. |
| 6 | `schedule` | 근무 스케줄. company/caregiver 의존. |
| 7 | `workrecord` | 근무 기록. schedule/worker 의존. |
| 8 | `document` | PDF/XLSX 출력 (WeasyPrint, openpyxl). 다른 도메인의 데이터 사용. |
| 9 | `job` | 백그라운드 작업 / 스케줄러. 마지막. |
| 10 | `board` | 게시판. 독립적이라 언제 옮겨도 됨 (병렬화 후보). |
| 11 | `notification` | FCM Push. firebase-admin. |

각 도메인 PR 의 Definition of Done:

- [ ] `app/domain/<name>/{router,service,repository,models,schemas}.py` 작성
- [ ] `api/v1/__init__.py` 에 라우터 마운트
- [ ] 응답 JSON 이 Java 와 동일 (스냅샷/계약 테스트 통과)
- [ ] 단위 테스트 + (가능하면) testcontainers 통합 테스트
- [ ] `src/main/java/com/rooti/domain/<name>/` 삭제
- [ ] Java 에서 더 이상 참조되지 않으면 관련 global/* 도 정리

---

## 5. 아직 안 한 것 (TODO)

- [ ] **인프라**: Dockerfile (Python 런타임). 현재 `docker/` 는 Java 기준.
- [ ] **CI**: `.github/workflows/` 의 Java 잡 옆에 Python 잡 추가 (ruff / mypy / pytest).
- [ ] **Flyway 분리**: Java 제거 후 `migrations/` 로 옮기고 `flyway/flyway` Docker 이미지로 실행.
- [ ] **OpenAPI 호환**: 한 번 Java/Python 양쪽에서 `/v3/api-docs` 를 받아 diff 떠보기 (클라이언트 생성기에 영향).
- [ ] **부하 비교**: 이행 직전, 동일 시나리오에서 p95/리소스 비교 (관측용 게이트).
- [ ] **로깅 traceId**: 미들웨어로 contextvars 'trace_id' 주입 (Java 의 MDC `[traceId]` 등가).

---

## 6. 자주 막히는 것

- **WeasyPrint 시스템 의존성**: macOS 는 `brew install pango gdk-pixbuf libffi`, Linux 는 `libcairo2 libpango-1.0-0 libpangoft2-1.0-0` 가 필요합니다 (OpenHTMLtoPDF 와 다르게 순수 자바가 아님).
- **asyncpg + SSL**: RDS 는 `sslmode=require` 가 기본. `config.py` 의 `db_ssl_required=True` 가 자동 처리.
- **passlib + bcrypt 4.x 경고**: `pyproject.toml` 의 `filterwarnings` 에서 무시 처리. 정식 픽스는 passlib 다음 릴리즈 대기.
