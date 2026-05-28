# Legacy migration — Django v1 → Rooti v2

This folder contains **one-shot** scripts that lift the data out of the Django
v1 schema (`auth_user`, `works_*`, `care_*`) and copy it into the v2 schema
(`users`, `companies`, `work_schedules`, ...) on the same RDS instance.

> The v2 (FastAPI) server has **no Django dependency at runtime**. We run this
> once during cutover and from then on the application only talks to v2 tables.

## Running the migration

```bash
# 1. Take a snapshot of your RDS instance (in the RDS console).

# 2. From your bastion / VPN box, connect with a user that has DDL on the DB:
psql \
  "postgresql://rooti_admin@<v2-prod-rds-endpoint>.ap-northeast-2.rds.amazonaws.com:5432/rooti?sslmode=require"

# 3. Apply the v2 schema first (creates v2 tables alongside the Django ones).
#    These are the same migration files the app/tests use.
\i ../migrations/V1__init_schema.sql
\i ../migrations/V2__seed_default_data.sql
\i ../migrations/V3__perf_indexes.sql
\i ../migrations/V4__kiosk_display_columns.sql
\i ../migrations/V5__leaves.sql
\i ../migrations/V6__journal_email_schedules.sql

# 4. Apply the data migration (copies Django rows into v2 tables).
\i V1-django-to-spring.sql

# 5. Verify (counts should match):
\i V2-verify.sql

# 6. Once the app is live and verified, drop the old Django tables.
\i V3-drop-django-tables.sql
```

## How the data maps

| Django (v1)                            | v2                                | Notes                                              |
| -------------------------------------- | --------------------------------- | -------------------------------------------------- |
| `auth_user`                            | `users`                           | role inferred from auxiliary table membership      |
| `works_company`                        | `companies`                       | `template_data` JSONField → JSONB                  |
| `works_companycharger`                 | `company_chargers`                |                                                    |
| `works_challengedworker`               | `challenged_workers`              |                                                    |
| `works_companyworker`                  | `company_workers`                 |                                                    |
| `works_jobstandard` / `_jobprocess`    | `job_standards` / `job_processes` | `context` JSONField → JSONB                        |
| `works_jobworker`                      | `job_workers`                     |                                                    |
| `works_workschedule`                   | `work_schedules`                  |                                                    |
| `works_workrecord` / `_workprocessrecord` | `work_records` / `work_process_records` |                                              |
| `works_companytohivitspetkiosk`        | `company_kiosks`                  |                                                    |
| `care_caregiver`                       | `caregivers`                      |                                                    |
| `care_caregivertochallengedworker`     | `caregiver_worker_relations`      |                                                    |
| `care_caregiverdocumenttype`           | `caregiver_document_types`        | `request_on` enum string preserved                 |
| `care_caregiverdocument`               | `caregiver_documents`             |                                                    |
| `care_caregiverdocumentlog`            | `caregiver_document_logs`         |                                                    |
| `care_caregiverboard`                  | `caregiver_boards`                | CKEditor `text` field → `body` column              |

## Idempotency

`V1-django-to-spring.sql` is wrapped in a single `BEGIN; ... COMMIT;` block and
uses `INSERT ... ON CONFLICT DO NOTHING` so re-running it is safe. Sequences
are bumped to `max(id)+1` at the end so subsequent INSERTs by the app don't
collide.

> Filenames keep the historical `-spring` suffix (the migration predates the
> FastAPI port); the target schema is identical, so the scripts are unchanged.
