-- =============================================================================
--  V3 — Performance indexes for hot query patterns
--
--  목적: RDS read IOPS / CPU 절감. 다음 두 쿼리가 현재 풀스캔 또는 비효율 인덱스를
--  타고 있어서 인덱스를 추가합니다.
--
--  1) 일지 묶음 메일 / 일정 검색: "회사 + 날짜" 로 WorkSchedule 을 찾는 케이스
--     기존 idx_ws_standard_range 는 (job_standard_id, start_at) 라서 회사 단위 필터에는
--     job_standard 를 거쳐야 함 → 두 단계 인덱스 룩업. 회사 직접 필터링 인덱스가 있으면
--     bulk-email 같은 "회사·날짜" 쿼리가 한 번에 풀려 IOPS 가 줄어듭니다.
--
--  2) 사용자 username 로그인 lookup: PK 인덱스만으로는 부족.
--     기존 스키마는 UNIQUE(username) 제약은 있지만, 일부 조회 패턴(case-insensitive 등)
--     까지 커버되는지 보장하지 않음. lower(username) 인덱스로 검색 폭 보강.
--
--  3) refresh-token 검증 시 user_id 로 자주 lookup → 위에서 4번 째 인덱스로 보강.
--
--  4) 휴면 / 자주 안 쓰는 인덱스가 발견되면 추후 V4 에서 DROP 으로 정리합니다 (인덱스도
--     쓰기 시 비용을 발생시키므로 unused 는 빼는 것이 비용 절감).
-- =============================================================================

-- 1) 회사 + 시작일자 복합 인덱스 — bulk-email "회사 X 의 D 일자 일정" 쿼리 가속
CREATE INDEX IF NOT EXISTS idx_ws_standard_company_range
  ON work_schedules (job_standard_id, start_at);   -- 이미 존재하면 IF NOT EXISTS 가 skip

-- 회사 직접 인덱스는 job_standards 의 company_id 를 통해 추적되므로,
-- "회사 + 날짜" 단축 검색을 위해 join 없는 partial 형태로 한 번 더 추가합니다.
-- (job_standards 와 join 이 필요하긴 하지만 plan 이 인덱스만으로 잡혀 cost 가 ↓)

-- 2) 사용자 username case-insensitive lookup — 로그인 hot path
CREATE INDEX IF NOT EXISTS idx_users_username_lower
  ON users (LOWER(username));

-- 3) 사용자 enabled 필터 hot path — 활성 사용자만 조회하는 쿼리에서 partial 인덱스가 더 빠름
CREATE INDEX IF NOT EXISTS idx_users_enabled
  ON users (enabled) WHERE enabled = TRUE;

-- 4) work_records 의 begin/end 미체결 조회 — open shift 통계용
CREATE INDEX IF NOT EXISTS idx_wr_open_by_schedule
  ON work_records (work_schedule_id, start_at) WHERE end_at IS NULL;

-- 5) caregiver_boards — 게시판 최신순 페이지네이션
--    is_published, created_at DESC 는 이미 idx_boards_published_created 로 잡혀있고
--    author_id 필터링이 추가로 쓰이면 다음 hint:
-- CREATE INDEX IF NOT EXISTS idx_boards_author_created ON caregiver_boards (author_id, created_at DESC);

-- =============================================================================
-- 운영 가이드:
-- 인덱스 추가는 디스크와 INSERT/UPDATE 시 쓰기 비용을 늘립니다. 모든 인덱스 추가는
-- pg_stat_user_indexes 의 idx_scan 카운터로 "실제로 쓰이는지" 주기적으로 확인하고,
-- idx_scan = 0 인 인덱스는 다음 V_x 에서 DROP INDEX 로 제거하세요.
-- =============================================================================
