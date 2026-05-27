# Contributing

## 로컬 셋업

```bash
make server-install   # uv venv + 의존성
make up-infra         # docker compose: postgres + redis
make server-migrate   # Flyway (Docker)
make server           # uvicorn on :8080 + /swagger-ui.html
```

## 커밋 컨벤션

Conventional Commits + 한국어 본문.

| Type | 용도 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `perf` | 성능 (비용 절감 포함) |
| `security` | 보안 강화 |
| `refactor` | 동작 동일, 구조 개선 |
| `test` | 테스트 추가/수정 |
| `docs` | 문서 |
| `chore` | 빌드/CI/툴링/잡일 |

scope 표기: `feat(auth):` `perf(db):` 처럼 명시. 한 커밋은 **한 가지 의도**만.

PR 본문은 다음을 포함:
- **무엇을 / 왜** (Java 시절과 비교 포함)
- 측정 가능한 임팩트 (있을 시): "N+1 → 1 query", "60% 응답 압축"
- 회귀 방지: 테스트 신규/수정 목록
- 보안 영향 (있을 시 — `SECURITY.md` 와 함께 검토)

## 코드 스타일

- Python 3.12, 모든 코드 ruff format 통과
- 타입 힌트는 모든 public API 에 필수 (`Mapped[]`, `Annotated[]` 사용)
- snake_case 변수/함수, PascalCase 클래스
- 한 모듈은 한 도메인 관심사만 — 1000 줄 넘으면 분할 고려

```bash
make server-fmt       # ruff format + check --fix
```

## 테스트

- 단위 테스트 — DB/Redis mock (conftest 의 `client` 픽스처)
- 통합 테스트 — testcontainers Postgres + 실 Flyway 마이그레이션 적용 (`live_client` 픽스처)
- 새 라우터/서비스 추가 시 OpenAPI route-lock 테스트 (`tests/test_openapi_routes.py`) 갱신 잊지 말 것

```bash
make server-test
```

## PR 흐름

1. `feat/...` / `perf/...` / `security/...` 등 prefix 로 브랜치
2. main 기준 분기 — 한 PR 의 stack 깊이는 1 (PR-on-PR 금지)
3. 커밋은 논리 단위로 분할 (대형 PR 도 commits 하나씩 리뷰 가능하게)
4. CI 통과 + 보안 영향 검토 → 머지

## 보안 이슈 제보

[SECURITY.md](SECURITY.md) 참조 — 공개 이슈로 올리지 말 것.
