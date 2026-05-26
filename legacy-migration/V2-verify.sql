-- Quick row-count sanity check after running V1-django-to-spring.sql.
-- Source and destination counts should match.
SELECT 'users'                     AS table,
       (SELECT count(*) FROM auth_user)                          AS src,
       (SELECT count(*) FROM users)                              AS dst
UNION ALL SELECT 'companies',
       (SELECT count(*) FROM works_company),
       (SELECT count(*) FROM companies)
UNION ALL SELECT 'company_chargers',
       (SELECT count(*) FROM works_companycharger),
       (SELECT count(*) FROM company_chargers)
UNION ALL SELECT 'challenged_workers',
       (SELECT count(*) FROM works_challengedworker),
       (SELECT count(*) FROM challenged_workers)
UNION ALL SELECT 'company_workers',
       (SELECT count(*) FROM works_companyworker),
       (SELECT count(*) FROM company_workers)
UNION ALL SELECT 'job_standards',
       (SELECT count(*) FROM works_jobstandard),
       (SELECT count(*) FROM job_standards)
UNION ALL SELECT 'job_processes',
       (SELECT count(*) FROM works_jobprocess),
       (SELECT count(*) FROM job_processes)
UNION ALL SELECT 'job_workers',
       (SELECT count(*) FROM works_jobworker),
       (SELECT count(*) FROM job_workers)
UNION ALL SELECT 'work_schedules',
       (SELECT count(*) FROM works_workschedule),
       (SELECT count(*) FROM work_schedules)
UNION ALL SELECT 'work_records',
       (SELECT count(*) FROM works_workrecord),
       (SELECT count(*) FROM work_records)
UNION ALL SELECT 'work_process_records',
       (SELECT count(*) FROM works_workprocessrecord),
       (SELECT count(*) FROM work_process_records)
UNION ALL SELECT 'caregivers',
       (SELECT count(*) FROM care_caregiver),
       (SELECT count(*) FROM caregivers)
UNION ALL SELECT 'caregiver_documents',
       (SELECT count(*) FROM care_caregiverdocument),
       (SELECT count(*) FROM caregiver_documents)
UNION ALL SELECT 'caregiver_boards',
       (SELECT count(*) FROM care_caregiverboard),
       (SELECT count(*) FROM caregiver_boards)
ORDER BY table;
