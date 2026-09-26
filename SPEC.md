# crosspath-id-server 実装仕様 v1（確定・実装者向け）

作業ディレクトリ: `/home/goshieta/work/crosspath-id-server`（git 管理下）
根拠設計: ノート「災害時すれ違い通信：初回登録・ID管理サーバー詳細設計 v1.0」の 3〜6節・9節・10節
役割: Android アプリ「災害時すれ違い通信」の **初回登録・24bit 個人ID 採番サーバー**。BLE・生存登録・72時間期限は本サーバーの責務外。

技術確定:
- Java 21 / Spring Boot **3.5.16** / Maven / PostgreSQL 16+ / Flyway / JdbcTemplate（JPA は使わない）
- テスト: JUnit 5 + Testcontainers(PostgreSQL)
- コンテナ: Docker（multi-stage）で Cloud Run に配備
- パッケージ基底: `com.crosspath.idservice`

**この仕様書に書かれた内容は推測せず、そのまま実装すること。仕様に無い独自解釈（追加エンドポイント・追加ライブラリ・追加機能）を足さないこと。**

---

## 1. 成果物ファイル一覧

```
crosspath-id-server/
  pom.xml
  SPEC.md（これ）
  README.md
  LICENSE                    # MIT, copyright holder "goshieta"
  .gitignore
  .dockerignore
  Dockerfile
  docker-compose.yml
  src/main/java/com/crosspath/idservice/
      IdServerApplication.java
      api/RegistrationController.java
      api/dto/RegistrationRequest.java
      api/dto/RegistrationResponse.java
      api/dto/ErrorResponse.java
      api/ApiExceptionHandler.java
      api/TraceIdFilter.java
      api/RequestSizeLimitFilter.java
      api/RateLimitFilter.java
      api/ClientIpResolver.java
      api/CacheControlFilter.java
      domain/CredentialSecret.java          # secret の検証・ハッシュ化
      domain/RequestIdValidator.java
      domain/RegistrationService.java       # 採番トランザクション本体
      domain/RegistrationResult.java
      domain/ApiErrorCode.java              # enum（HTTP status / code / retryable）
      domain/ApiException.java
      persistence/IdAllocatorRepository.java
      persistence/RegistrationRepository.java
      persistence/RegistrationRow.java
      config/RateLimitProperties.java
      config/AppProperties.java（必要なら）
  src/main/resources/
      application.yml
      db/migration/V1__init.sql
      db/migration/V2__grants_readme.sql は作らない（権限は deploy/sql/grants.sql で実施）
  src/test/java/com/crosspath/idservice/  （§9 の試験）
  deploy/
      README.md
      sql/grants.sql
      10-enable-apis.sh
      20-create-sql.sh
      30-secret.sh
      40-build-push.sh
      50-deploy-cloudrun.sh
      60-sql-start.sh
      61-sql-stop.sh
      99-smoke-test.sh
```

---

## 2. DB スキーマ（V1__init.sql・設計書 5節のとおり）

```sql
CREATE TABLE id_allocator (
    singleton SMALLINT PRIMARY KEY CHECK (singleton = 1),
    next_id INTEGER NOT NULL CHECK (next_id BETWEEN 1 AND 16777216)
);
INSERT INTO id_allocator(singleton, next_id) VALUES (1, 1);

CREATE TABLE registrations (
    user_id INTEGER PRIMARY KEY CHECK (user_id BETWEEN 1 AND 16777215),
    request_id UUID NOT NULL UNIQUE,
    credential_hash BYTEA NOT NULL UNIQUE
        CHECK (octet_length(credential_hash) = 32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    state TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (state IN ('ACTIVE', 'RETIRED'))
);
```

- `next_id = 16777216` は枯渇番兵。個人ID として発行しない。
- 追加のテーブル・列・索引を勝手に足さない（UNIQUE/PK が索引を作る）。`RETIRED` への更新は運用（管理者）が行うもので、API からは行わない。

---

## 3. API 契約

### 3.1 `POST /v1/registrations`

リクエスト:
```http
POST /v1/registrations HTTP/1.1
Content-Type: application/json
Authorization: Bearer <registration_secret>     # 43文字 base64url・パディング無し＝32バイト
```
```json
{ "request_id": "78ea85f0-09b5-4b1b-b830-2fcb29711601" }
```

- 認可は **Bearer secret のみ**。`registration_secret` はクライアントが生成した 32 バイト乱数の base64url（=url-safe, no padding, 43 文字）。復号して 32 バイトにならなければ 401。
- ボディは JSON オブジェクトでキーは `request_id` ただ 1 つ。未知フィールド・欠落・null・型違いは 400。
- `request_id` は UUID **v4** のみ許可（version nibble=4、variant=10xx）。v1/v7 や不正形式は 400。
- ボディサイズ上限 1024 バイト（`Content-Length` 超過、または読取時超過で 400 INVALID_REQUEST）。
- `Content-Type` が `application/json` 以外 → 400。

処理（**単一 DB トランザクション**、設計書 6節の手順を厳守）:
1. 形式検証・レート制限・secret ハッシュ化（SHA-256）はトランザクション外。
2. `BEGIN`（READ COMMITTED）。トランザクション先頭で `SET LOCAL lock_timeout = '2s'`（'2000ms' でも可）。
3. `SELECT next_id FROM id_allocator WHERE singleton = 1 FOR UPDATE`
   - 行が無い → 台帳破損として扱う。INSERT し直さない。ERROR ログを出し **500 INTERNAL_ERROR**。
4. `SELECT user_id, request_id, credential_hash, created_at, state FROM registrations WHERE request_id = ?`
   - 見つかった場合:
     - hash 一致 かつ state=ACTIVE → **200**（既存の値で応答。採番しない）
     - hash 不一致 → **409 REGISTRATION_CONFLICT**（採番しない）
     - hash 一致 かつ state=RETIRED → **410 REGISTRATION_RETIRED**（採番しない）
5. `SELECT user_id, request_id, state FROM registrations WHERE credential_hash = ?`
   - 別 request_id で使用済み → **409 REGISTRATION_CONFLICT**（採番しない）
6. `next_id > 16777215` → **503 ID_SPACE_EXHAUSTED**（`retryable=false`）
7. `user_id = next_id` として `INSERT INTO registrations(user_id, request_id, credential_hash, created_at, state) VALUES (?, ?, ?, CURRENT_TIMESTAMP, 'ACTIVE')`
8. `UPDATE id_allocator SET next_id = next_id + 1 WHERE singleton = 1`
9. `COMMIT` 成功を確認してから応答。**commit 前に成功応答してはならない。**
10. 新規発行は **201**、既存要求の再送は **200**。

- `credential_hash` の比較は **定数時間比較**（`MessageDigest.isEqual`）を使う。
- `user_id` PK 衝突（＝台帳不整合）は握りつぶさず、ERROR ログ＋**500 INTERNAL_ERROR**。既存行を上書きしない。
- ロックタイムアウト（SQLState 55P03 / 57014 等）→ **503 REGISTRATION_BUSY**（`Retry-After: 1`、`retryable=true`）。
- その他の SQLException → ロールバックして 500/503（`INTERNAL_ERROR` / `SERVICE_UNAVAILABLE`、`retryable=true`）。

成功ボディ（200/201 共通・DB 保存値から組み立てる）:
```json
{ "request_id": "78ea85f0-09b5-4b1b-b830-2fcb29711601", "user_id": 123456, "created_at": "2026-09-26T03:00:00Z" }
```
- `created_at`: DB の `created_at` を **UTC・秒精度**（`yyyy-MM-dd'T'HH:mm:ss'Z'`）で出力。

### 3.2 `GET /v1/registrations/me`

- `Authorization: Bearer <secret>` のみ。`credential_hash` で検索。
- 見つかった（state=ACTIVE）→ **200** と §3.1 と同じ形のボディ。
- 見つかった（state=RETIRED）→ **410 REGISTRATION_RETIRED**。
- 見つからない・secret 不正 → **401 INVALID_CREDENTIAL**。
- 認証失敗のレスポンス・ログに既存 ID・既存資格情報を含めてはならない。

### 3.3 エラー形式（全エラー共通）

```json
{ "error": { "code": "INVALID_REQUEST", "retryable": false } }
```
`message` フィールドは任意（付ける場合も秘密値・内部 SQL・スタックトレースを入れない）。

| HTTP | code | retryable |
|---|---|---|
| 400 | INVALID_REQUEST | false |
| 401 | INVALID_CREDENTIAL | false |
| 404 | NOT_FOUND（未知パスのみ） | false |
| 405 | METHOD_NOT_ALLOWED | false |
| 409 | REGISTRATION_CONFLICT | false |
| 410 | REGISTRATION_RETIRED | false |
| 429 | RATE_LIMITED | true |
| 500 | INTERNAL_ERROR | true |
| 503 | REGISTRATION_BUSY | true |
| 503 | ID_SPACE_EXHAUSTED | false |
| 503 | SERVICE_UNAVAILABLE | true |

- 429 / 503 には `Retry-After`（秒）を付与。REGISTRATION_BUSY は 1、RATE_LIMITED は再試行可能までの秒数、SERVICE_UNAVAILABLE は 5。
- **全レスポンス**（エラー含む）に `Cache-Control: no-store` を付与する。
- Spring のデフォルトエラーページ（HTML/`timestamp`入りJSON）を出さないこと。`@RestControllerAdvice` で全ての例外を上表へ変換し、`server.error.whitelabel.enabled=false` も設定する。

---

## 4. レート制限（設計書 9.1・初期案）

- per-IP: **10 要求/分・瞬間バースト 20**（トークンバケット、上限 20 トークン、毎秒 10/60 補充）。
- グローバル: **新規発行 10 件/秒**（新規発行に進む要求のみ消費。再送はカウントしない）。
- 環境変数で上書き可能にする（`RATE_LIMIT_PER_IP_PER_MINUTE`, `RATE_LIMIT_PER_IP_BURST`, `RATE_LIMIT_NEW_REGISTRATIONS_PER_SECOND`）。
- クライアント IP は `X-Forwarded-For` の先頭値、無ければ remote address。
- インメモリ実装でよい（初期は 1 インスタンス）。README に「複数インスタンス化する場合は共有ストアが必要」と明記。
- 超過時は **429 RATE_LIMITED**。

---

## 5. 設定・運用要件

`application.yml`:
- ポートは `SERVER_PORT`（既定 8080）。Cloud Run は `PORT` を渡すので `SERVER_PORT=${PORT:8080}` とする。
- DB: `DB_URL`（既定 `jdbc:postgresql://localhost:5432/crosspath`）、`DB_USER`、`DB_PASSWORD`。
- Flyway: `DB_MIGRATION_USER` / `DB_MIGRATION_PASSWORD`（未設定なら `DB_USER`/`DB_PASSWORD` にフォールバック）。**マイグレーションは所有者ロール、実行時接続は権限を絞ったアプリロール**で行う。
- Hikari: `maximumPoolSize=4`, `minimumIdle=1`, `connectionTimeout=5000`, `validationTimeout=3000`（db-f1-micro を考慮した小さめ設定。環境変数で上書き可）。
- Graceful shutdown: `server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=20s`。
- JSON: 未知プロパティはエラー（`spring.jackson.deserialization.fail-on-unknown-properties: true`）。
- Actuator: `health` のみ公開（`management.endpoints.web.exposure.include=health`、`management.endpoint.health.probes.enabled=true`、DB ヘルスを含める）。
- アクセスログ（Tomcat）は無効。`logging.level.root=INFO`。
- ログに Authorization ヘッダ・リクエスト本文・secret・credential_hash・user_id を出さない。記録するのは traceId・処理時間(ms)・HTTPステータス・エラーコードのみ。`TraceIdFilter` で採番した traceId を `X-Trace-Id` レスポンスヘッダにも返す。

`deploy/sql/grants.sql`（所有者ロールで実行、`crosspath_app` をアプリ用ロールとする）:
```sql
GRANT CONNECT ON DATABASE crosspath TO crosspath_app;
GRANT USAGE ON SCHEMA public TO crosspath_app;
GRANT SELECT, INSERT ON registrations TO crosspath_app;
GRANT SELECT, UPDATE ON id_allocator TO crosspath_app;
REVOKE DELETE, TRUNCATE ON registrations, id_allocator FROM crosspath_app;
```
（`crosspath` / `crosspath_app` の名前はデプロイスクリプトの変数に合わせて調整してよい。README に明記）

---

## 6. Dockerfile / docker-compose

- `Dockerfile`: multi-stage。
  - build: `maven:3.9-eclipse-temurin-21` で `mvn -B -DskipTests package`（依存キャッシュのため pom 先コピー → go-offline → src コピー）。
  - runtime: `eclipse-temurin:21-jre-alpine`（無ければ `21-jre`）。非 root ユーザで実行。`EXPOSE 8080`。
  - `ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]`。`JAVA_OPTS` で追加上書き可能。
- `docker-compose.yml`: `postgres:16-alpine`（healthcheck 付き、volume、POSTGRES_DB=crosspath）+ `app`（depends_on: service_healthy、8080 公開、DB_* 環境変数）。
- `.gitignore`: target/, .idea, *.iml, .env, *.log
- `.dockerignore`: .git, target, .idea, *.md（SPEC/READMEは残してよい）

---

## 7. デプロイスクリプト（`deploy/`・bash・`set -euo pipefail`）

環境変数（各スクリプト冒頭で既定値を定義、上書き可）:
`PROJECT_ID=crosspath-id-server` / `REGION=asia-northeast1` / `SQL_INSTANCE=crosspath-pg` / `DB_NAME=crosspath` / `APP_USER=crosspath_app` / `OWNER_USER=crosspath` / `AR_REPO=crosspath` / `SERVICE=id-server`。

- `10-enable-apis.sh`: run, sqladmin, artifactregistry, cloudbuild, secretmanager を enable。
- `20-create-sql.sh`: Cloud SQL PostgreSQL 16、**db-f1-micro / 共有コア / zonal（HA無し）/ ストレージ 10GB HDD / `--no-backup` にしない（backup 有効・保持1世代）** で作成。`OWNER_USER` と `APP_USER` を作成し、`deploy/sql/grants.sql` を所有者で適用。パスワードはランダム生成して Secret Manager に保存。
  - 注意: コスト最小化のため **HDD 10GB・エディションは ENTERPRISE（既定）・HA 無効**。作成完了を待つ。
- `30-secret.sh`: Secret Manager に `db-app-password` / `db-owner-password` を整形して保存（冪等: 既存なら新バージョン追加）。
- `40-build-push.sh`: Artifact Registry リポジトリ作成（docker, asia-northeast1）→ `gcloud builds submit --tag` でイメージビルド＆push。
- `50-deploy-cloudrun.sh`: `gcloud run deploy` で
  - `--image`, `--region`, `--allow-unauthenticated`, `--port=8080`, `--memory=512Mi`, `--cpu=1`, `--min-instances=0`, `--max-instances=2`, `--concurrency=20`, `--timeout=30`, `--cpu-throttling`（既定=有効）, `--add-cloudsql-instances=$PROJECT_ID:$REGION:$SQL_INSTANCE`, `--set-env-vars=DB_URL=...,DB_USER=...,DB_MIGRATION_USER=...`, `--set-secrets=DB_PASSWORD=db-app-password:latest,DB_MIGRATION_PASSWORD=db-owner-password:latest`, `--no-cpu-boost`。
  - `DB_URL` は unix socket 形式: `jdbc:postgresql:///${DB_NAME}?host=/cloudsql/${PROJECT_ID}:${REGION}:${SQL_INSTANCE}&socketTimeout=15&connectTimeout=10`
  - 成功後に URL を出力。
- `60-sql-start.sh`: `gcloud sql instances patch $SQL_INSTANCE --activation-policy=ALWAYS`
- `61-sql-stop.sh`: `gcloud sql instances patch $SQL_INSTANCE --activation-policy=NEVER`（コスト停止。ストレージ課金のみ残る）
- `99-smoke-test.sh`: `BASE_URL` を引数/環境変数で受け、curl で
  1. `GET /actuator/health` → 200 かつ status UP
  2. 新規登録（secret/request_id は `openssl rand` で生成）→ 201 かつ user_id が 1〜16777215
  3. 同一要求の再送 → 200 かつ **同じ user_id**
  4. 別 secret・同一 request_id → 409
  5. `GET /v1/registrations/me` → 200 かつ同じ user_id
  6. 壊れた JSON → 400
  7. 全応答に `Cache-Control: no-store` が付くこと
  を検証し、結果を表で出力して 1 つでも失敗したら非 0 で終了。
- `deploy/README.md`: 実行順、コスト（db-f1-micro 常時 ~$8-10/月、停止時 ~$1/月、Cloud Run は無料枠内）、起動/停止運用、ロールバック。

---

## 8. README.md（リポジトリ直下・日本語）

以下を含む:
1. 概要（災害時すれ違い通信アプリの初回登録・24bit 個人ID 発行サーバー。BLE/生存登録は対象外）
2. アーキテクチャ図（Android SC01 → HTTPS → Cloud Run → Cloud SQL）
3. API 契約（§3 の表・例・エラー表）
4. データモデル（§2）と ID 仕様（1〜16,777,215、0 予約、再利用なし、枯渇時挙動）
5. ローカル開発（docker compose up、mvn test）
6. GCP デプロイ手順（deploy/ の順番、必要な権限）
7. **コスト**（構成の内訳、普段は SQL 停止、無料枠の範囲、注意点）
8. セキュリティ・運用上の制約（署名なし＝本人性は未検証、バックアップ巻き戻し禁止、複数インスタンス時のレート制限、1ID1端末・復旧なし）
9. 試験と対応表（§9 のどのテストが設計書 S01〜S18 に対応するか）
10. ライセンス（MIT）

---

## 9. テスト（`src/test/java`・Testcontainers `postgres:16-alpine`）

モジュール単位:
- `CredentialSecretTest`: 43 文字 base64url を受けて 32 バイトに復号 / 長さ違い・base64 不正・パディング付きを拒否 / SHA-256 ハッシュが 32 バイト / 定数時間比較ヘルパの動作。
- `RequestIdValidatorTest`: v4 UUID 受理、v1/v7・非 UUID・欠落を拒否。
- `ApiExceptionHandlerTest`（MockMvc, `@WebMvcTest`）: 400/401/404/405 のボディ形式と `Cache-Control: no-store`。

統合（`@SpringBootTest(webEnvironment=RANDOM_PORT)` + Testcontainers、`RestClient`/`TestRestTemplate`）:
- **S01** 異なる 100 要求を並行送信 → user_id 100 件が一意、1〜100 が漏れなく発行。
- **S02** 同一 request_id・secret を 100 回並行送信 → `registrations` 1 行、返る user_id が全一致。
- **S03** 同一 request_id・別 secret → 409、行数増えない、他人の ID を返さない。
- **S04** 同一 secret・別 request_id → 409、その後 `GET /me` で既存 ID が取れる。
- **S05** 成功後の同一 POST 再送 → 200 かつ同じボディ（request_id/user_id/created_at）。
- **S08** `next_id` を 16777215 に更新した状態 → 1 件だけ 16777215 が発行され、次は 503 ID_SPACE_EXHAUSTED、既存要求の再送は 200。
- **S09** SQL 直接 INSERT で user_id=0 / -1 / 16777216 → CHECK 制約違反。`next_id` の範囲外も CHECK 違反。
- **S10** state=RETIRED（SQL で更新）→ POST は 410、GET も 410、ID 再発行なし。
- **S14** 別コネクションで採番行をロック保持したまま POST → 503 REGISTRATION_BUSY（`Retry-After` 付き）、余分な ID が発行されない。
- **S17** 不正 JSON / 1025 バイト超ボディ / secret 不正（39 文字・パディング付き）→ 400 または 401。レスポンス本文に secret・`credential_hash`・SQL 文字列が含まれない。
- `GET /me`: 未登録 secret → 401。
- `CacheControlNoStoreTest`: 正常・エラー応答すべてに `Cache-Control: no-store`。
- `HealthEndpointTest`: `/actuator/health` が 200。

テストは必ず `docker` が使える環境で `mvn -B verify` で通ること（Testcontainers は `postgres:16-alpine`）。テストを無効化・skip して通したら **未完了**とみなす。

---

## 10. 完了条件（自己申告だけで終わらせない）

1. `docker run --rm -v "$PWD":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock maven:3.9-eclipse-temurin-21 mvn -B -q verify` が **成功**（Testcontainers を使うので docker.sock を渡す）。
2. `docker build -t crosspath-id-server:local .` が成功。
3. 失敗した場合は原因を直して再実行し、最終的に緑にする。直せない場合は、失敗ログの要点と未解決点を REPORT.md に書き、正直に報告する。
4. 実装完了時に `REPORT.md` を作り、以下を簡潔に記載: 実装したファイル一覧 / 実行したコマンドと結果 / 仕様から逸脱した点（あれば理由）/ 未検証の点。

守ること:
- 秘密情報のハードコード禁止（パスワード・トークン）。
- 仕様にないエンドポイントや機能を追加しない。
- コメントは要点のみ。日本語コメント可。
- 生成物はすべて `/home/goshieta/work/crosspath-id-server` 配下に置く。
