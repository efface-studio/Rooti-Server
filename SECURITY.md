# Security policy

## 취약점 제보

Rooti-Server 의 잠재적 보안 취약점을 발견하신 경우 **공개 이슈로 올리지 마시고**
다음 경로로 비공개 제보 부탁드립니다:

- 이메일: `security@hi-vits.com`
- 또는 GitHub Security Advisory (`Security` 탭 → `Report a vulnerability`)

대응 SLA:
- 영업일 기준 **2일 내** 접수 확인 회신
- **30일 내** 패치 또는 mitigation 적용 (영향도에 따라 단축)
- 패치 배포 후 CVE / 어드바이저리 게시, 제보자 크레딧

## 지원 버전

| 버전 | 보안 패치 |
|---|---|
| `main` | ✅ 항상 최신 |
| 태그 release (`vN.M.x`) | 최근 2개 minor 만 |

## 보안 통제 요약 (현재 적용된 것)

### 인증·인가
- JWT (HS256), issuer `rooti`, claims `sub/usn/roles/typ`
- `JWT_SECRET` 32 byte 미만 부팅 거부 ([config.py](app/core/config.py))
- Access token TTL 15 min · Refresh token TTL 14 days
- Refresh 토큰 **회전 + 재사용 탐지** — 탈취된 토큰 재사용 시 해당 사용자의 모든 세션 강제 폐기 ([services/auth.py](app/services/auth.py))
- BCrypt cost 10 (Java BCryptPasswordEncoder 호환)
- Role guard: `@RequireAdmin` / `@RequireAdminOrCharger` / `@RequireCaregiver` ([api/deps.py](app/api/deps.py))

### IDOR / 권한 분리
- Caregiver 문서 접근은 `relation.caregiver.user_id == actor.user_id OR actor.role == ADMIN` 게이트 ([services/document.py](app/services/document.py))
- 게시판 수정/삭제는 author-only
- Work-journal PDF/XLSX 다운로드는 ADMIN/CHARGER 만 (worker 본인 IDOR 차단)

### HTTP 헤더
[middleware.py](app/core/middleware.py):
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Permissions-Policy: geolocation=() microphone=() camera=() ...`
- `Content-Security-Policy: default-src 'self' ...`
- `Strict-Transport-Security: max-age=31536000` (prod 만)
- `Cache-Control: no-store` (JSON 응답)

### 입력 검증
- Pydantic 모든 요청 body 검증
- 파일 업로드: MIME 화이트리스트 + 50 MB 상한 + 파일명 위험 문자 차단 ([upload_validation.py](app/core/upload_validation.py))
- LocalDiskStorage: 경로 탈출 (`..` 등) 가드 ([integrations/storage.py](app/integrations/storage.py))

### 정보 누출 방지
- 운영(`app_env=prod`) 의 500 응답은 일반 메시지만 — exception class / stack 비공개 ([exceptions.py](app/core/exceptions.py))
- `/actuator/health` 컴포넌트별 status 만 (DB/Redis 에러 raw 문자열 응답 제거) ([health.py](app/api/health.py))
- 로그인 실패는 `AUTH_INVALID_CREDENTIALS` 한 가지로 통일 — username vs password 어디서 틀렸는지 누설 X

### 네트워크 / 한도
- CORS: `CORS_ALLOWED_ORIGINS` 화이트리스트
- Rate limit (slowapi + Redis backend): login 5/min · refresh 10/min · signup 5/min · 전역 200/min ([rate_limit.py](app/core/rate_limit.py))
- Pagination 상한 100 row ([pagination.py](app/core/pagination.py))
- Schedule list date-range 31 일 cap

### 의존성
- `poetry update` 정기적 수행 (CI 에서 `pip-audit` 로 CVE 스캔)
- `dependabot` 활성 (PR 5 에서 추가)

## 알려진 한도 / 후속 작업

- **CHARGER 의 company 격리**: 현재는 CHARGER role 이면 모든 회사의 worker/kiosk/job-standard 에 접근 가능. Java 원본도 동일. 후속에 `company_charger.company_id` 기반 격리 도입 예정.
- **HWP 렌더링**: 한글 워드프로세서 포맷은 placeholder (텍스트 fallback). 정식 SDK 통합 시 교체.
