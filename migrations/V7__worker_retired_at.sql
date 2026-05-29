-- =============================================================================
--  V7: 근로자 퇴직 처리 (challenged_workers.retired_at)
--
--  NULL  → 재직 중(ACTIVE)
--  값 존재 → 해당 시각에 퇴직(RETIRED). 재고용 시 다시 NULL 로.
--  관리자 화면의 근로자 목록 상태 필터(employmentStatus)에서 사용.
-- =============================================================================
ALTER TABLE challenged_workers ADD COLUMN IF NOT EXISTS retired_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_challenged_workers_retired ON challenged_workers (retired_at);
