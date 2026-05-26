-- =============================================================================
--  Django v1 → Spring V2 data migration.
--
--  Prerequisite: the V2 schema (Flyway V1 + V2) has already been applied to
--  this database, so the target tables exist and are empty.
--
--  This script is idempotent: every INSERT uses ON CONFLICT DO NOTHING, and
--  the closing block resets each sequence to max(id)+1 so Spring's IDENTITY
--  generator doesn't collide with the existing rows.
-- =============================================================================
BEGIN;

-- ----------------------------------------------------------------------------
--  USERS  (Django auth_user → users)
--  Role is derived from membership in the role-specific Django tables.
-- ----------------------------------------------------------------------------
INSERT INTO users (id, username, email, password_hash, name, phone_number,
                   role, enabled, last_login_at, created_at, updated_at)
SELECT
    u.id,
    u.username,
    NULLIF(u.email, ''),
    u.password,
    COALESCE(NULLIF(TRIM(u.first_name || ' ' || u.last_name), ''), u.username),
    NULL                                                            AS phone_number,
    CASE
        WHEN u.is_superuser OR u.is_staff                           THEN 'ADMIN'
        WHEN EXISTS (SELECT 1 FROM works_companycharger c WHERE c.user_id = u.id) THEN 'CHARGER'
        WHEN EXISTS (SELECT 1 FROM care_caregiver g WHERE g.user_id = u.id)        THEN 'CAREGIVER'
        WHEN EXISTS (SELECT 1 FROM works_challengedworker w WHERE w.user_id = u.id) THEN 'WORKER'
        ELSE 'WORKER'
    END                                                             AS role,
    u.is_active,
    u.last_login,
    COALESCE(u.date_joined, CURRENT_TIMESTAMP),
    COALESCE(u.date_joined, CURRENT_TIMESTAMP)
FROM auth_user u
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
--  COMPANIES
-- ----------------------------------------------------------------------------
INSERT INTO companies (id, name, location, use_flag, image_path, template_id,
                       template_data, created_at, updated_at)
SELECT
    c.id,
    c.name,
    c.location,
    COALESCE(c.use_flag, TRUE),
    c.image_path,
    c.template_id,
    -- Django JSONField is stored as jsonb in modern Django, fall through.
    c.template_data,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM works_company c
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
--  COMPANY CHARGER (담당자)
-- ----------------------------------------------------------------------------
INSERT INTO company_chargers (id, user_id, company_id, is_hired, fcm_token,
                              created_at, updated_at)
SELECT
    cc.id,
    cc.user_id,
    cc.company_id,
    COALESCE(cc.is_hired, TRUE),
    cc.fcm_token,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM works_companycharger cc
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
--  CHALLENGED WORKER (장애인 근로자)
-- ----------------------------------------------------------------------------
INSERT INTO challenged_workers (id, user_id, fcm_token, created_at, updated_at)
SELECT cw.id, cw.user_id, cw.fcm_token, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM works_challengedworker cw
ON CONFLICT (id) DO NOTHING;

-- Phone number lived on the Django side at `works_challengedworker.phone_number`.
-- Push it onto the corresponding user row (only if user has none yet).
UPDATE users u
   SET phone_number = cw.phone_number
  FROM works_challengedworker cw
 WHERE u.id = cw.user_id
   AND (u.phone_number IS NULL OR u.phone_number = '');

-- Same for caregivers (phone lived on `care_caregiver`).
UPDATE users u
   SET phone_number = COALESCE(u.phone_number, g.phone_number)
  FROM care_caregiver g
 WHERE u.id = g.user_id
   AND (u.phone_number IS NULL OR u.phone_number = '');

-- ----------------------------------------------------------------------------
--  COMPANY WORKER (회사-근로자 채용)
-- ----------------------------------------------------------------------------
INSERT INTO company_workers (id, company_id, challenged_worker_id, is_hired,
                             created_at, updated_at)
SELECT
    cw.id,
    cw.company_id,
    cw.challenged_worker_id,
    COALESCE(cw.is_hired, TRUE),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM works_companyworker cw
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
--  JOB STANDARD / PROCESS / WORKER
-- ----------------------------------------------------------------------------
INSERT INTO job_standards (id, company_id, name, use_flag, routine_start_time,
                           standard_work_time, standard_rest_time,
                           start_message, end_message, context, for_journal,
                           created_at, updated_at)
SELECT
    js.id, js.company_id, js.name, COALESCE(js.use_flag, TRUE),
    js.routine_start_time,
    -- v1 may have stored seconds as integer already; if it's a duration,
    -- EXTRACT below pulls seconds. Adjust if your column type differs.
    COALESCE(js.standard_work_time, 0),
    COALESCE(js.standard_rest_time, 0),
    js.start_message, js.end_message,
    js.context,
    COALESCE(js.for_journal, FALSE),
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM works_jobstandard js
ON CONFLICT (id) DO NOTHING;

INSERT INTO job_processes (id, job_standard_id, name, sequence, video_path,
                           start_message, end_message, context, process_time,
                           created_at, updated_at)
SELECT
    jp.id, jp.job_standard_id, jp.name, COALESCE(jp.sequence, 0),
    jp.video_path, jp.start_message, jp.end_message, jp.context,
    COALESCE(jp.process_time, 0),
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM works_jobprocess jp
ON CONFLICT (id) DO NOTHING;

INSERT INTO job_workers (id, company_worker_id, job_standard_id, use_flag,
                         context, created_at, updated_at)
SELECT
    jw.id, jw.company_worker_id, jw.job_standard_id,
    COALESCE(jw.use_flag, TRUE), jw.context,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM works_jobworker jw
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
--  SCHEDULES & RECORDS
-- ----------------------------------------------------------------------------
INSERT INTO work_schedules (id, job_worker_id, company_charger_id,
                            job_standard_id, start_at, end_at, work_doc_path,
                            make_work_doc, created_at, updated_at)
SELECT
    ws.id, ws.job_worker_id, ws.company_charger_id, ws.job_standard_id,
    ws.start_at, ws.end_at, ws.work_doc_path,
    COALESCE(ws.make_work_doc, FALSE),
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM works_workschedule ws
ON CONFLICT (id) DO NOTHING;

INSERT INTO work_records (id, work_schedule_id, type, start_at, end_at,
                          created_at, updated_at)
SELECT
    wr.id, wr.work_schedule_id, UPPER(wr.type),
    wr.start_at, wr.end_at,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM works_workrecord wr
ON CONFLICT (id) DO NOTHING;

INSERT INTO work_process_records (id, work_schedule_id, job_process_id, type,
                                  start_at, end_at, start_condition,
                                  end_condition, start_answer, end_answer,
                                  start_voice_path, end_voice_path, process,
                                  created_at, updated_at)
SELECT
    wpr.id, wpr.work_schedule_id, wpr.job_process_id, COALESCE(wpr.type, 'PROCESS'),
    wpr.start_at, wpr.end_at,
    wpr.start_condition::smallint, wpr.end_condition::smallint,
    wpr.start_answer, wpr.end_answer,
    wpr.start_voice, wpr.end_voice,
    wpr.process,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM works_workprocessrecord wpr
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
--  CAREGIVERS & RELATIONS
-- ----------------------------------------------------------------------------
INSERT INTO caregivers (id, user_id, created_at, updated_at)
SELECT g.id, g.user_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM care_caregiver g
ON CONFLICT (id) DO NOTHING;

INSERT INTO caregiver_worker_relations (id, caregiver_id, challenged_worker_id,
                                        created_at, updated_at)
SELECT r.id, r.caregiver_id, r.challenged_worker_id,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM care_caregivertochallengedworker r
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
--  CAREGIVER DOCUMENTS
-- ----------------------------------------------------------------------------
INSERT INTO caregiver_document_types (id, name, description, request_on,
                                      created_at, updated_at)
SELECT
    t.id, t.name, t.description,
    UPPER(COALESCE(t.request_on, 'nothing')),
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM care_caregiverdocumenttype t
ON CONFLICT (id) DO NOTHING;

INSERT INTO caregiver_documents (id, relation_id, type_id, filename,
                                 created_at, updated_at)
SELECT
    d.id, d.relation_id, d.document_type_id, d.filename,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM care_caregiverdocument d
ON CONFLICT (id) DO NOTHING;

INSERT INTO caregiver_document_logs (id, document_id, user_id, action_type, action_at)
SELECT
    l.id, l.document_id, l.user_id, UPPER(l.action_type), l.action_at
FROM care_caregiverdocumentlog l
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
--  BOARDS  (text → body)
-- ----------------------------------------------------------------------------
INSERT INTO caregiver_boards (id, author_id, title, body, is_published,
                              created_at, updated_at)
SELECT
    b.id,
    -- Django's CaregiverBoard had no author field in v1; default to admin user id 1.
    COALESCE(1, 1),
    b.title,
    b.text,
    COALESCE(b.is_published, TRUE),
    COALESCE(b.created_at, CURRENT_TIMESTAMP),
    COALESCE(b.created_at, CURRENT_TIMESTAMP)
FROM care_caregiverboard b
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
--  KIOSKS & OPTION VARIABLES
-- ----------------------------------------------------------------------------
INSERT INTO company_kiosks (id, company_id, kiosk_id, created_at, updated_at)
SELECT k.id, k.company_id, k.kiosk_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM works_companytohivitspetkiosk k
ON CONFLICT (id) DO NOTHING;

INSERT INTO option_variables (id, name, for_what, value, created_at, updated_at)
SELECT o.id, o.name, o.for_what, o.value,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM works_optionvariable o
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
--  BUMP SEQUENCES so Spring IDENTITY generators don't collide with v1 rows.
-- ----------------------------------------------------------------------------
DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT c.oid::regclass AS table_name, a.attname AS column_name,
               pg_get_serial_sequence(c.oid::regclass::text, a.attname) AS seq
          FROM pg_class c
          JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'id'
         WHERE c.relname IN (
            'users','companies','company_chargers','challenged_workers',
            'company_workers','caregivers','caregiver_worker_relations',
            'job_standards','job_processes','job_workers',
            'work_schedules','work_records','work_process_records',
            'caregiver_document_types','caregiver_documents',
            'caregiver_document_logs','caregiver_boards',
            'company_kiosks','option_variables'
         )
    LOOP
        IF r.seq IS NOT NULL THEN
            EXECUTE format(
                'SELECT setval(%L, COALESCE((SELECT MAX(id) FROM %s), 0) + 1, false)',
                r.seq, r.table_name
            );
        END IF;
    END LOOP;
END $$;

COMMIT;
