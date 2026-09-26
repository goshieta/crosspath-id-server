-- V2: アプリロール crosspath_app への権限付与
-- 所有者ロール（マイグレーション実行ユーザー）で実行する。
-- Testcontainers でも動作するよう、ロールが無ければ作成する。

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'crosspath_app') THEN
        CREATE ROLE crosspath_app LOGIN;
    END IF;
END
$$;

-- Testcontainers では current_database() が crosspath とは限らないため動的SQLを使用
DO $$
DECLARE
    db_name TEXT;
BEGIN
    db_name := current_database();
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO crosspath_app', db_name);
END
$$;

GRANT USAGE ON SCHEMA public TO crosspath_app;
GRANT SELECT, INSERT ON registrations TO crosspath_app;
GRANT SELECT, UPDATE ON id_allocator TO crosspath_app;
REVOKE DELETE, TRUNCATE ON registrations, id_allocator FROM crosspath_app;