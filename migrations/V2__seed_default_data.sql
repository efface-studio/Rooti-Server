-- =============================================================================
--  V2: Seed defaults
--    * one bootstrap super-admin user (password = "admin1234", bcrypt-encoded)
--    * canonical caregiver document types
--    * option variables that Django used to seed at runtime
-- =============================================================================

-- bcrypt('admin1234', 10) — change immediately after first login
INSERT INTO users (username, email, password_hash, name, role, enabled)
VALUES ('admin', 'admin@rooti.io',
        '$2a$10$ZcJlGpDt9rsf1Ldf6IIYme8Y04mECJ3uVtsoMxjyhYQzVD3LSx7zG',
        'Root Admin', 'ADMIN', TRUE);

INSERT INTO caregiver_document_types (name, description, request_on) VALUES
    ('가족관계증명서', '근로자와의 가족관계 증명',            'REGISTER'),
    ('주민등록등본',   '주소/세대 확인용',                    'REGISTER'),
    ('장애인등록증',   '근로자의 장애 등록 증빙',             'REGISTER'),
    ('근로계약서',     '회사-근로자 간 근로계약',             'NOTHING'),
    ('기타',           '그 외 보호자 제출 자료',              'NOTHING')
ON CONFLICT (name) DO NOTHING;

INSERT INTO option_variables (name, for_what, value) VALUES
    ('latest_version',        'app',  '1.0.0'),
    ('min_supported_version', 'app',  '1.0.0')
ON CONFLICT (name) DO NOTHING;
