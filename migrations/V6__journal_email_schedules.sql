-- =============================================================================
--  V6: 근무일지 메일 자동 발송 스케줄
--
--  회사별로 "매일/매주/매월 특정 시각에 근무일지 ZIP 을 담당자 메일로 자동 발송" 설정.
--  실제 발송 트리거(스케줄러)는 별도 (job 도메인/크론) — 이 테이블은 설정과 다음 실행시각만 보관.
-- =============================================================================
CREATE TABLE journal_email_schedules (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT      NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    recipient_email VARCHAR(255) NOT NULL,
    frequency       VARCHAR(20) NOT NULL,
    weekday         VARCHAR(3),                       -- WEEKLY 일 때 (SUN..SAT)
    day_of_month    INTEGER,                          -- MONTHLY 일 때 (1..31)
    send_time       VARCHAR(5)  NOT NULL,             -- 'HH:MM'
    format          VARCHAR(10) NOT NULL DEFAULT 'PDF',
    enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
    next_run_at     TIMESTAMP,
    last_run_at     TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT jes_frequency_chk CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY')),
    CONSTRAINT jes_format_chk    CHECK (format    IN ('PDF','HWP','XLSX')),
    CONSTRAINT jes_weekday_chk   CHECK (weekday IS NULL OR weekday IN ('SUN','MON','TUE','WED','THU','FRI','SAT'))
);
CREATE INDEX idx_jes_company ON journal_email_schedules (company_id);
CREATE INDEX idx_jes_enabled ON journal_email_schedules (enabled);
CREATE TRIGGER jes_set_updated BEFORE UPDATE ON journal_email_schedules
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
