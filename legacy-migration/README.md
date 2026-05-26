# Legacy migration — Django v1 → Spring V2

This folder contains **one-shot** scripts that lift the data out of the Django
v1 schema (`auth_user`, `works_*`, `care_*`) and copy it into the V2 Spring
schema (`users`, `companies`, `work_schedules`, ...) on the same RDS instance.

> The Spring server has **no Django dependency at runtime**. We run this once
> during cutover and from then on the application only talks to V2 tables.

## Running the migration

```bash
# 1. Take a snapshot of your RDS instance (in the RDS console).

# 2. From your bastion / VPN box, connect with a user that has DDL on the DB:
psql \
  "postgresql://rooti_admin@rooti-prod.xxxxxxxx.ap-northeast-2.rds.amazonaws.com:5432/rooti?sslmode=require"

# 3. Apply the V2 schema first (creates V2 tables alongside the Django ones).
\i ../src/main/resources/db/migration/V1__init_schema.sql

# 4. Apply the data migration (copies Django rows into V2 tables).
\i V1-django-to-spring.sql

# 5. Verify (counts should match):
\i V2-verify.sql

# 6. Once the app is live and verified, drop the old Django tables.
\i V3-drop-django-tables.sql
```

## How the data maps

| Django (v1)                            | Spring (V2)                       | Notes                                              |
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
are bumped to `max(id)+1` at the end so subsequent INSERTs by Spring don't
collide.
