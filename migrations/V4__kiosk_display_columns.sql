-- =============================================================================
--  V4: company_kiosks 에 표시/상태 컬럼 추가
--
--  관리자 키오스크 화면이 이름/위치/정원/현재인원/상태/담당자/마지막보고 를 보여주는데
--  기존 테이블엔 (company_id, kiosk_id) 뿐이라 컬럼 보강. 모두 nullable/default 라 기존 행 안전.
-- =============================================================================
ALTER TABLE company_kiosks
    ADD COLUMN IF NOT EXISTS name             VARCHAR(200),
    ADD COLUMN IF NOT EXISTS location         VARCHAR(500),
    ADD COLUMN IF NOT EXISTS capacity         INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS current_count    INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS status           VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    ADD COLUMN IF NOT EXISTS assignee         VARCHAR(200),
    ADD COLUMN IF NOT EXISTS last_reported_at TIMESTAMP;

ALTER TABLE company_kiosks
    DROP CONSTRAINT IF EXISTS company_kiosks_status_chk;
ALTER TABLE company_kiosks
    ADD CONSTRAINT company_kiosks_status_chk CHECK (status IN ('IN_USE', 'FULL', 'OFFLINE'));

CREATE INDEX IF NOT EXISTS idx_company_kiosks_status ON company_kiosks (status);
