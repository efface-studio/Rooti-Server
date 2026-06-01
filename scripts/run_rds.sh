#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# v2 서버를 DEV RDS(db-vitsdevelopmentserver)에만 붙여 실행한다.
#
#   cp .env.rds.example .env.rds   # 한 번
#   ./scripts/run_rds.sh
#
# .env.rds(gitignore)에서 DB_* 를 읽고, 나머지(JWT/Redis/Storage/CORS)는
# .env.dev 에서 상속된다. 호스트가 dev RDS 가 아니면 실행을 거부한다.
# ⚠️ 본서버(production)/임의 호스트 금지.
#
# 공유 dev RDS 를 보호하기 위해 기본은 READ-ONLY(보내기 비활성)다 —
# 읽기(불러오기)는 정상, 쓰기는 깔끔한 503(READ_ONLY)로 막힌다.
#   APP_READ_ONLY=false ./scripts/run_rds.sh   # 쓰기(보내기)까지 활성화
#   SERVER_PORT=8088     ./scripts/run_rds.sh   # 포트 변경(기본 8080)
# -----------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f .env.rds ] || { echo "ERROR: .env.rds 없음 — 'cp .env.rds.example .env.rds' 후 값 채우기"; exit 1; }

set -a
# shellcheck disable=SC1091
. ./.env.rds
set +a

case "${DB_HOST:-}" in
  *db-vitsdevelopmentserver*) : ;;
  *) echo "REFUSING: DB_HOST='${DB_HOST:-}' 는 dev RDS 가 아닙니다 (본서버/타호스트 금지)"; exit 1 ;;
esac

# 안전 기본값: 공유 dev RDS 는 read-only 로 붙는다 (호출자가 명시하면 그 값 우선).
export APP_READ_ONLY="${APP_READ_ONLY:-true}"

mode="READ-ONLY (불러오기만, 보내기 차단)"
[ "${APP_READ_ONLY}" = "true" ] || mode="READ-WRITE (보내기 활성)"
echo "→ uvicorn on RDS ${DB_HOST} / db=${DB_NAME:-?} (ssl=${DB_SSL_REQUIRED:-?})  [${mode}]  port=${SERVER_PORT:-8080}"
exec poetry run uvicorn app.main:app --host 127.0.0.1 --port "${SERVER_PORT:-8080}"
