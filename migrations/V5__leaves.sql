-- =============================================================================
--  V5: 휴가(leaves) 테이블
--
--  근로자 휴가 신청/승인. 회사·근로자 양 화면(근무일지 산정)에서 참조되므로 company_id 필수.
--  start_date~end_date 포함(inclusive), days 는 서버가 계산해 캐시.
-- =============================================================================
CREATE TABLE leaves (
    id          BIGSERIAL PRIMARY KEY,
    worker_id   BIGINT      NOT NULL REFERENCES challenged_workers(id) ON DELETE CASCADE,
    company_id  BIGINT      NOT NULL REFERENCES companies(id)          ON DELETE RESTRICT,
    type        VARCHAR(20) NOT NULL,
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    days        INTEGER     NOT NULL DEFAULT 1,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reason      TEXT,
    created_by  BIGINT,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT leaves_type_chk   CHECK (type   IN ('ANNUAL','MONTHLY','SICK','OTHER')),
    CONSTRAINT leaves_status_chk CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT leaves_range_chk  CHECK (end_date >= start_date)
);
CREATE INDEX idx_leaves_worker   ON leaves (worker_id, start_date);
CREATE INDEX idx_leaves_company  ON leaves (company_id, start_date);
CREATE INDEX idx_leaves_status   ON leaves (status);
CREATE TRIGGER leaves_set_updated BEFORE UPDATE ON leaves
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
