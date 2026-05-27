-- =============================================================================
--  V1: Initial schema for the Rooti workforce-management platform.
--
--  Conventions
--    * snake_case for tables & columns
--    * BIGSERIAL primary keys (joins stay fast and indexable)
--    * audit columns (created_at / updated_at) on every table
--    * created_by / updated_by where an operator trail matters
--    * FK columns are always indexed (PG does NOT auto-index them)
--    * JSONB for "loose" structured payloads (originated from Django JSONField)
--    * CHECK constraints in lieu of an Enum table where the domain is closed and stable
-- =============================================================================

-- -----------------------------------------------------------------------------
--  Trigger helper: bump updated_at on every UPDATE. JPA also writes this column,
--  but the trigger guarantees consistency for raw SQL paths (data migrations etc).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
--  USERS  (단일 User 테이블, role 컬럼으로 분기. Django의 분산된 User+Profile 구조를 정규화)
-- =============================================================================
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(150) NOT NULL UNIQUE,
    email           VARCHAR(255),
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    phone_number    VARCHAR(32),
    role            VARCHAR(20)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_role_chk CHECK (role IN ('ADMIN','CHARGER','WORKER','CAREGIVER'))
);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_role  ON users (role);
CREATE TRIGGER users_set_updated BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- =============================================================================
--  COMPANIES + chargers + workers (회사·담당자·근로자 트리)
-- =============================================================================
CREATE TABLE companies (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    location        VARCHAR(500),
    use_flag        BOOLEAN      NOT NULL DEFAULT TRUE,
    image_path      VARCHAR(500),
    template_id     VARCHAR(100),
    template_data   JSONB,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    updated_by      BIGINT
);
CREATE INDEX idx_companies_name ON companies (name);
CREATE INDEX idx_companies_use  ON companies (use_flag);
CREATE TRIGGER companies_set_updated BEFORE UPDATE ON companies
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE company_chargers (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL UNIQUE REFERENCES users(id)     ON DELETE RESTRICT,
    company_id      BIGINT       NOT NULL        REFERENCES companies(id) ON DELETE RESTRICT,
    is_hired        BOOLEAN      NOT NULL DEFAULT TRUE,
    fcm_token       VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_company_chargers_company ON company_chargers (company_id);
CREATE TRIGGER company_chargers_set_updated BEFORE UPDATE ON company_chargers
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE challenged_workers (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    fcm_token       VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER challenged_workers_set_updated BEFORE UPDATE ON challenged_workers
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE company_workers (
    id                  BIGSERIAL PRIMARY KEY,
    company_id          BIGINT NOT NULL REFERENCES companies(id)         ON DELETE RESTRICT,
    challenged_worker_id BIGINT NOT NULL REFERENCES challenged_workers(id) ON DELETE RESTRICT,
    is_hired            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_company_workers UNIQUE (company_id, challenged_worker_id)
);
CREATE INDEX idx_company_workers_worker  ON company_workers (challenged_worker_id);
CREATE INDEX idx_company_workers_hired   ON company_workers (company_id, is_hired);
CREATE TRIGGER company_workers_set_updated BEFORE UPDATE ON company_workers
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- =============================================================================
--  CAREGIVERS
-- =============================================================================
CREATE TABLE caregivers (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT     NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    created_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER caregivers_set_updated BEFORE UPDATE ON caregivers
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE caregiver_worker_relations (
    id                  BIGSERIAL PRIMARY KEY,
    caregiver_id        BIGINT NOT NULL REFERENCES caregivers(id)         ON DELETE CASCADE,
    challenged_worker_id BIGINT NOT NULL REFERENCES challenged_workers(id) ON DELETE CASCADE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_caregiver_worker UNIQUE (caregiver_id, challenged_worker_id)
);
CREATE INDEX idx_cwr_worker ON caregiver_worker_relations (challenged_worker_id);
CREATE TRIGGER cwr_set_updated BEFORE UPDATE ON caregiver_worker_relations
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- =============================================================================
--  JOBS (표준 → 프로세스 → 근로자할당)
-- =============================================================================
CREATE TABLE job_standards (
    id                   BIGSERIAL PRIMARY KEY,
    company_id           BIGINT       NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    name                 VARCHAR(200) NOT NULL,
    use_flag             BOOLEAN      NOT NULL DEFAULT TRUE,
    routine_start_time   TIME,
    standard_work_time   INTEGER      NOT NULL DEFAULT 0,   -- seconds
    standard_rest_time   INTEGER      NOT NULL DEFAULT 0,   -- seconds
    start_message        TEXT,
    end_message          TEXT,
    context              JSONB,
    for_journal          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           BIGINT,
    updated_by           BIGINT
);
CREATE INDEX idx_job_standards_company ON job_standards (company_id);
CREATE INDEX idx_job_standards_use     ON job_standards (use_flag);
CREATE TRIGGER job_standards_set_updated BEFORE UPDATE ON job_standards
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE job_processes (
    id              BIGSERIAL PRIMARY KEY,
    job_standard_id BIGINT       NOT NULL REFERENCES job_standards(id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    sequence        INTEGER      NOT NULL DEFAULT 0,
    video_path      VARCHAR(500),
    start_message   TEXT,
    end_message     TEXT,
    context         JSONB,
    process_time    INTEGER      NOT NULL DEFAULT 0,        -- seconds
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_process_seq UNIQUE (job_standard_id, sequence)
);
CREATE INDEX idx_job_processes_standard ON job_processes (job_standard_id);
CREATE TRIGGER job_processes_set_updated BEFORE UPDATE ON job_processes
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE job_workers (
    id                BIGSERIAL PRIMARY KEY,
    company_worker_id BIGINT  NOT NULL REFERENCES company_workers(id) ON DELETE CASCADE,
    job_standard_id   BIGINT  NOT NULL REFERENCES job_standards(id)   ON DELETE CASCADE,
    use_flag          BOOLEAN NOT NULL DEFAULT TRUE,
    context           JSONB,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_job_worker UNIQUE (company_worker_id, job_standard_id)
);
CREATE INDEX idx_job_workers_standard ON job_workers (job_standard_id);
CREATE INDEX idx_job_workers_use      ON job_workers (job_standard_id, use_flag);
CREATE TRIGGER job_workers_set_updated BEFORE UPDATE ON job_workers
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- =============================================================================
--  WORK SCHEDULES + RECORDS + PROCESS RECORDS
-- =============================================================================
CREATE TABLE work_schedules (
    id                  BIGSERIAL PRIMARY KEY,
    job_worker_id       BIGINT    NOT NULL REFERENCES job_workers(id)       ON DELETE CASCADE,
    company_charger_id  BIGINT             REFERENCES company_chargers(id)  ON DELETE SET NULL,
    job_standard_id     BIGINT    NOT NULL REFERENCES job_standards(id)     ON DELETE RESTRICT,
    start_at            TIMESTAMP NOT NULL,
    end_at              TIMESTAMP,
    work_doc_path       VARCHAR(500),
    make_work_doc       BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT
);
CREATE INDEX idx_ws_jobworker_range ON work_schedules (job_worker_id, start_at);
CREATE INDEX idx_ws_standard_range  ON work_schedules (job_standard_id, start_at);
CREATE INDEX idx_ws_open            ON work_schedules (job_worker_id) WHERE end_at IS NULL;
CREATE TRIGGER work_schedules_set_updated BEFORE UPDATE ON work_schedules
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE work_records (
    id                BIGSERIAL PRIMARY KEY,
    work_schedule_id  BIGINT      NOT NULL REFERENCES work_schedules(id) ON DELETE CASCADE,
    type              VARCHAR(10) NOT NULL,
    start_at          TIMESTAMP   NOT NULL,
    end_at            TIMESTAMP,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT work_records_type_chk CHECK (type IN ('ON','WORK','REST','OFF'))
);
CREATE INDEX idx_wr_schedule  ON work_records (work_schedule_id);
CREATE INDEX idx_wr_type_open ON work_records (work_schedule_id, type) WHERE end_at IS NULL;
CREATE TRIGGER work_records_set_updated BEFORE UPDATE ON work_records
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE work_process_records (
    id               BIGSERIAL PRIMARY KEY,
    work_schedule_id BIGINT      NOT NULL REFERENCES work_schedules(id) ON DELETE CASCADE,
    job_process_id   BIGINT      NOT NULL REFERENCES job_processes(id)  ON DELETE RESTRICT,
    type             VARCHAR(20) NOT NULL,                              -- PROCESS|...
    start_at         TIMESTAMP,
    end_at           TIMESTAMP,
    start_condition  SMALLINT,                                          -- 1=OK, 0=NO, -1=OTHER
    end_condition    SMALLINT,
    start_answer     TEXT,
    end_answer       TEXT,
    start_voice_path VARCHAR(500),
    end_voice_path   VARCHAR(500),
    process          JSONB,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_wpr_schedule ON work_process_records (work_schedule_id);
CREATE INDEX idx_wpr_process  ON work_process_records (job_process_id);
CREATE TRIGGER wpr_set_updated BEFORE UPDATE ON work_process_records
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- =============================================================================
--  CAREGIVER DOCUMENTS (보호자 증명서)
-- =============================================================================
CREATE TABLE caregiver_document_types (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL UNIQUE,
    description TEXT,
    request_on  VARCHAR(20)  NOT NULL DEFAULT 'NOTHING',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cdt_request_on_chk CHECK (request_on IN ('NOTHING','REGISTER'))
);
CREATE TRIGGER cdt_set_updated BEFORE UPDATE ON caregiver_document_types
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE caregiver_documents (
    id           BIGSERIAL PRIMARY KEY,
    relation_id  BIGINT       NOT NULL REFERENCES caregiver_worker_relations(id) ON DELETE CASCADE,
    type_id      BIGINT       NOT NULL REFERENCES caregiver_document_types(id)   ON DELETE RESTRICT,
    filename     VARCHAR(500) NOT NULL,
    file_size    BIGINT,
    content_type VARCHAR(100),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   BIGINT,
    updated_by   BIGINT
);
CREATE INDEX idx_cd_relation ON caregiver_documents (relation_id);
CREATE INDEX idx_cd_type     ON caregiver_documents (type_id);
CREATE TRIGGER cd_set_updated BEFORE UPDATE ON caregiver_documents
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE caregiver_document_logs (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT      NOT NULL REFERENCES caregiver_documents(id) ON DELETE CASCADE,
    user_id      BIGINT      NOT NULL REFERENCES users(id)               ON DELETE RESTRICT,
    action_type  VARCHAR(20) NOT NULL,
    action_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cdl_action_chk CHECK (action_type IN ('UPLOAD','DOWNLOAD','DELETE'))
);
CREATE INDEX idx_cdl_document ON caregiver_document_logs (document_id);
CREATE INDEX idx_cdl_user     ON caregiver_document_logs (user_id);

-- =============================================================================
--  CAREGIVER BOARD
-- =============================================================================
CREATE TABLE caregiver_boards (
    id           BIGSERIAL PRIMARY KEY,
    author_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title        VARCHAR(255) NOT NULL,
    body         TEXT         NOT NULL,
    is_published BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   BIGINT,
    updated_by   BIGINT
);
CREATE INDEX idx_boards_published_created ON caregiver_boards (is_published, created_at DESC);
CREATE TRIGGER boards_set_updated BEFORE UPDATE ON caregiver_boards
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

-- =============================================================================
--  Kiosk binding & free-form option variables
-- =============================================================================
CREATE TABLE company_kiosks (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    kiosk_id    VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_company_kiosk UNIQUE (company_id, kiosk_id)
);
CREATE TRIGGER company_kiosks_set_updated BEFORE UPDATE ON company_kiosks
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

CREATE TABLE option_variables (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    for_what    VARCHAR(100),
    value       TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER opt_var_set_updated BEFORE UPDATE ON option_variables
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
