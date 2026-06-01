-- =============================================================================
--  V8: 근무일지 일괄 메일 발송 잡(이력/상태)
--
--  POST /work-journals/bulk-email 는 N건 일지를 render→ZIP→Resend 로 비동기 발송한다.
--  지금까지는 결과를 알 길이 없었으므로(fire-and-forget), 발송 1건마다 잡 row 를 남겨
--  진행 상태(QUEUED→SENDING→SUCCESS/FAILED)와 이력을 조회할 수 있게 한다.
--
--  status 전이:
--    QUEUED   요청 직후(엔드포인트가 기록) → 백그라운드 큐 대기
--    SENDING  백그라운드 러너가 렌더/발송 시작
--    SUCCESS  Resend 발송 완료(message_id 기록, 첨부 건수 schedule_count)
--    FAILED   렌더/발송 중 예외(error_message 기록)
-- =============================================================================
CREATE TABLE bulk_email_jobs (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT       NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    recipient_email VARCHAR(255) NOT NULL,
    target_date     DATE         NOT NULL,             -- 묶을 근무일지의 기준일
    format          VARCHAR(10)  NOT NULL DEFAULT 'PDF',
    status          VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',
    schedule_count  INTEGER      NOT NULL DEFAULT 0,    -- 실제 첨부된 일지 수
    truncated       BOOLEAN      NOT NULL DEFAULT FALSE, -- 상한 초과로 잘렸는지
    message_id      VARCHAR(255),                       -- Resend message id (성공 시)
    error_message   TEXT,                               -- 실패 사유 (실패 시)
    requested_by    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    started_at      TIMESTAMP,                          -- SENDING 진입 시각
    finished_at     TIMESTAMP,                          -- SUCCESS/FAILED 도달 시각
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT bej_status_chk CHECK (status IN ('QUEUED','SENDING','SUCCESS','FAILED')),
    CONSTRAINT bej_format_chk CHECK (format IN ('PDF','HWP','XLSX'))
);
-- 이력은 회사별 최신순 조회가 대부분 — (company_id, id desc) 커버링.
CREATE INDEX idx_bej_company_id ON bulk_email_jobs (company_id, id DESC);
CREATE INDEX idx_bej_status ON bulk_email_jobs (status);
CREATE TRIGGER bej_set_updated BEFORE UPDATE ON bulk_email_jobs
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
