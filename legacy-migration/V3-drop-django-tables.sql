-- =============================================================================
--  Run this ONLY after:
--    1) V1-django-to-spring.sql has been applied
--    2) V2-verify.sql showed src == dst counts everywhere
--    3) The new V2 application has been live for at least one business day
--       and you are confident you no longer need to roll back to Django.
--
--  Tables are dropped with CASCADE so leftover Django-only indexes/views drop
--  alongside them.
-- =============================================================================
BEGIN;

DROP TABLE IF EXISTS care_caregiverdocumentlog        CASCADE;
DROP TABLE IF EXISTS care_caregiverdocument           CASCADE;
DROP TABLE IF EXISTS care_caregiverdocumenttype       CASCADE;
DROP TABLE IF EXISTS care_caregivertochallengedworker CASCADE;
DROP TABLE IF EXISTS care_caregiverboard              CASCADE;
DROP TABLE IF EXISTS care_caregiver                   CASCADE;

DROP TABLE IF EXISTS works_workprocessrecord          CASCADE;
DROP TABLE IF EXISTS works_workrecord                 CASCADE;
DROP TABLE IF EXISTS works_workschedule               CASCADE;
DROP TABLE IF EXISTS works_jobworker                  CASCADE;
DROP TABLE IF EXISTS works_jobprocess                 CASCADE;
DROP TABLE IF EXISTS works_jobstandard                CASCADE;
DROP TABLE IF EXISTS works_companytohivitspetkiosk    CASCADE;
DROP TABLE IF EXISTS works_companyworker              CASCADE;
DROP TABLE IF EXISTS works_companycharger             CASCADE;
DROP TABLE IF EXISTS works_challengedworker           CASCADE;
DROP TABLE IF EXISTS works_company                    CASCADE;
DROP TABLE IF EXISTS works_optionvariable             CASCADE;

-- Django framework tables — drop after you've moved auth_user data over.
DROP TABLE IF EXISTS django_admin_log                 CASCADE;
DROP TABLE IF EXISTS auth_user_user_permissions       CASCADE;
DROP TABLE IF EXISTS auth_user_groups                 CASCADE;
DROP TABLE IF EXISTS auth_user                        CASCADE;
DROP TABLE IF EXISTS auth_permission                  CASCADE;
DROP TABLE IF EXISTS auth_group_permissions           CASCADE;
DROP TABLE IF EXISTS auth_group                       CASCADE;
DROP TABLE IF EXISTS django_content_type              CASCADE;
DROP TABLE IF EXISTS django_session                   CASCADE;
DROP TABLE IF EXISTS django_migrations                CASCADE;
DROP TABLE IF EXISTS django_celery_results_taskresult CASCADE;
DROP TABLE IF EXISTS django_celery_results_chordcounter CASCADE;
DROP TABLE IF EXISTS django_celery_results_groupresult CASCADE;
DROP TABLE IF EXISTS django_celery_beat_clockedschedule CASCADE;
DROP TABLE IF EXISTS django_celery_beat_intervalschedule CASCADE;
DROP TABLE IF EXISTS django_celery_beat_crontabschedule CASCADE;
DROP TABLE IF EXISTS django_celery_beat_solarschedule CASCADE;
DROP TABLE IF EXISTS django_celery_beat_periodictask CASCADE;
DROP TABLE IF EXISTS django_celery_beat_periodictasks CASCADE;

COMMIT;
