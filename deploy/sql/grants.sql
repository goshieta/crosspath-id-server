-- deploy/sql/grants.sql (所有者ロールで実行)
-- 注意: Flyway V2 マイグレーション (V2__grants.sql) でも同等の権限付与を行うため、
-- こちらは手動実行時の代替手段として維持する。V2 マイグレーションが適用済みなら不要。
-- Flyway の migration user を所有者ロールに設定し、V2__grants.sql で権限付与すること。

GRANT CONNECT ON DATABASE crosspath TO crosspath_app;
GRANT USAGE ON SCHEMA public TO crosspath_app;
GRANT SELECT, INSERT ON registrations TO crosspath_app;
GRANT SELECT, UPDATE ON id_allocator TO crosspath_app;
REVOKE DELETE, TRUNCATE ON registrations, id_allocator FROM crosspath_app;