# REPORT_FIX.md

## 修正一覧

### 修正1: UUID型不一致

**症状**: `org.postgresql.util.PSQLException: ERROR: operator does not exist: uuid = character varying`

**原因**: `RegistrationRepository` の ROW_MAPPER で `UUID.fromString(rs.getString("request_id"))` と文字列経由で UUID を取得していた。また `findByRequestId()` や `insert()` で `requestId.toString()` を JDBC に渡しており、PostgreSQL の UUID 型と文字列の比較になっていた。

**対応ファイル**: `src/main/java/com/crosspath/idservice/persistence/RegistrationRepository.java`
- ROW_MAPPER: `UUID.fromString(rs.getString("request_id"))` → `rs.getObject("request_id", UUID.class)`
- `findByRequestId()`: 第3引数 `requestId.toString()` → `requestId`（UUID のまま JdbcTemplate に渡す）
- `insert()`: 第3引数 `requestId.toString()` → `requestId`

---

### 修正2: 新規発行の created_at は DB 保存値を返す

**症状**: SPEC §3.1「成功本文の値は DB に保存した値から組み立てる」に反し、`OffsetDateTime.now()` で Java 側時刻を使っていた。

**対応**: 
- `RegistrationRepository.insert()`: SQL に `RETURNING created_at` を追加し、`OffsetDateTime` を返すように変更（戻り値型 `void` → `OffsetDateTime`）。`queryForObject` で INSERT 結果の created_at を直接受け取る。
- `RegistrationService.register()`: `OffsetDateTime now()` の代わりに `insert()` の戻り値を使用。既存レコード再送時（200）と新規発行（201）で同じ DB 由来の値を返す。

---

### 修正3: ロックタイムアウトを 503 REGISTRATION_BUSY に写像

**症状**: 従来 `DataAccessException` を一律 `INTERNAL_ERROR(500)` にしていたため、PostgreSQL ロックタイムアウトが 503 REGISTRATION_BUSY にならなかった。

**対応ファイル**: `src/main/java/com/crosspath/idservice/domain/RegistrationService.java`
- `PessimisticLockingFailureException`（SQLState 55P03 含む）と `QueryTimeoutException`（SQLState 57014 含む）を捕捉し、`ApiException(ApiErrorCode.REGISTRATION_BUSY, null, 1)` に変換（Retry-After: 1）。
- 内側の `selectNextIdForUpdate()` 呼び出しで捕捉 + 外側の try-catch で全手順共通の捕捉を二重に配置。例外は外側で再度捕捉されず、最初に捕捉された方がそのまま ApiException として伝播する（外側の catch は、内側で捕捉されなかった手順2/4/5/8 などからの例外のセーフティネット）。
- `DataAccessException` の捕捉は従来通り（id_allocator 行が存在しない場合 → INTERNAL_ERROR）。lock 系は DataAccessException より先に捕捉される（Java の catch 順序）。
- `CannotAcquireLockException` は `PessimisticLockingFailureException` のサブクラスのため除去。

---

### 修正4: application.yml の重複キー

**調査結果**: 現在の `src/main/resources/application.yml` には `server:` ブロックは1つしか存在せず、`port` / `shutdown` / `error.whitelabel.enabled` / `tomcat.accesslog.enabled` はすべて1つのブロック内に収まっていた。重複する `server:` キーは見つからなかったため、コード上の変更は不要と判断した。

（SPEC §5 の記述は独立した箇条書きになっているため誤解を招きやすいが、実際の YAML は正しく統合されている。）

---

### 修正5: レート制限の扱い

#### (a) ヘルスチェック・ルートパスの除外

**対応ファイル**: `src/main/java/com/crosspath/idservice/api/RateLimitFilter.java`
- `doFilterInternal()` 冒頭で `/actuator/health` と `/` を検査し、該当する場合はフィルターチェーンを続行してすぐに return（レート制限制御をスキップ）。

#### (b) グローバルレート制限の移動

**背景**: SPEC §4 の「新規発行 10 件/秒」は新規発行に進む要求のみ消費させる必要がある。従来の RateLimitFilter ではすべての POST /v1/registrations で消費していたため、再送（既存 request_id）でもトークンを消費していた。

**対応**:
- 新規ファイル `src/main/java/com/crosspath/idservice/config/GlobalRateLimiter.java` を作成。`RateLimitProperties` の `newRegistrationsPerSecond` を読み取り、トークンバケットを管理する。
- `RegistrationService.register()` に `GlobalRateLimiter` を注入。手順 6.5（枯渇チェックの後・INSERT の前）で `tryConsume()` を呼び、消費できなければ `RATE_LIMITED(429)` を返す（Retry-After: 1）。
- 再送パス（手順4で既存レコードACTIVEが一致）ではグローバルレート制限を消費しない（早期 return）。
- `GlobalRateLimiter` も `DISABLE_RATE_LIMIT=true` に対応（テスト環境で無効化）。
- `RateLimitFilter` からグローバルバケットと `RATE_LIMIT_NEW_REGISTRATIONS_PER_SECOND` の読み取りを削除。Javadoc も更新。

---

### 修正6: DBロールへの権限付与マイグレーションの追加

**新規ファイル**: `src/main/resources/db/migration/V2__grants.sql`
- `crosspath_app` ロールが存在しなければ作成（`CREATE ROLE ... LOGIN`）
- `current_database()` を用いた動的 SQL で `GRANT CONNECT ON DATABASE`（Testcontainers でもデータベース名が異なっても動作）
- `GRANT USAGE ON SCHEMA public`
- `GRANT SELECT, INSERT ON registrations`
- `GRANT SELECT, UPDATE ON id_allocator`
- `REVOKE DELETE, TRUNCATE ON registrations, id_allocator`

**更新ファイル**:
- `deploy/20-create-sql.sh`: grants.sql の説明を「V2 マイグレーション優先」に変更
- `deploy/README.md`: 権限付与の説明を V2 マイグレーション方式に更新
- `README.md`: DB 権限付与の項目を追加し、V2 マイグレーション方式を説明
- `deploy/sql/grants.sql`: コメントを更新し、V2 マイグレーションが優先であることを明記

---

## 変更ファイル一覧

| ファイル | 変更内容 |
|---|---|
| `src/main/java/com/crosspath/idservice/persistence/RegistrationRepository.java` | UUID 型統一 + RETURNING created_at |
| `src/main/java/com/crosspath/idservice/domain/RegistrationService.java` | DB由来created_at + ロック例外→REGISTRATION_BUSY + グローバルレート制限注入 |
| `src/main/java/com/crosspath/idservice/api/RateLimitFilter.java` | パス除外 + グローバルバケット除去 |
| `src/main/java/com/crosspath/idservice/config/GlobalRateLimiter.java` | **新規作成** |
| `src/main/resources/db/migration/V2__grants.sql` | **新規作成** |
| `deploy/sql/grants.sql` | コメント更新 |
| `deploy/20-create-sql.sh` | V2 マイグレーション優先の注釈 |
| `deploy/README.md` | 権限付与方式の更新 |
| `README.md` | DB 権限付与の説明追加 |
| `src/main/resources/application.yml` | 変更なし（重複キーは存在しなかった） |

---

## 未検証点

- フルテスト（`mvn verify`）は未実行（実行制約のため）。親による実行を待つ。
- グローバルレート制限の `Retry-After: 1` が正確かどうかは負荷試験による調整が必要な可能性がある。
- V2 マイグレーションが Testcontainers（ユーザー名 `test`）で正しく動作するかは実行確認が必要。