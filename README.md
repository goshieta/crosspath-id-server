# crosspath-id-server

災害時すれ違い通信アプリ（Android）の **初回登録・24bit 個人ID 採番サーバー**。

BLE（すれ違い通信）・生存登録・72時間期限 は本サーバーの責務外。

## アーキテクチャ

```
Android 端末 (SC01) → HTTPS → Cloud Run (id-server) → Cloud SQL (PostgreSQL 16)
```

## API 契約

### `POST /v1/registrations`

新規登録または既存登録の再送。

リクエスト:
```
POST /v1/registrations HTTP/1.1
Content-Type: application/json
Authorization: Bearer <registration_secret>
```

```json
{ "request_id": "78ea85f0-09b5-4b1b-b830-2fcb29711601" }
```

- `registration_secret`: クライアントが生成した 32 バイト乱数の base64url (url-safe, no padding, 43 文字)
- `request_id`: UUID v4 のみ

応答:

| 状況 | HTTP ステータス | ボディ |
|---|---|---|
| 新規登録 | **201** | `{ "request_id", "user_id", "created_at" }` |
| 再送（同一 secret + request_id） | **200** | 同上（初回と同じ値） |

### `GET /v1/registrations/me`

登録済み secret から自分の ID を取得。

### エラーコード一覧

| HTTP | code | retryable | 意味 |
|---|---|---|---|
| 400 | INVALID_REQUEST | false | リクエスト形式不正 |
| 401 | INVALID_CREDENTIAL | false | 認証情報不正 |
| 404 | NOT_FOUND | false | 未知のパス |
| 405 | METHOD_NOT_ALLOWED | false | HTTP メソッド不正 |
| 409 | REGISTRATION_CONFLICT | false | request_id または secret の重複・競合 |
| 410 | REGISTRATION_RETIRED | false | 登録済みが無効化されている |
| 429 | RATE_LIMITED | true | レート制限超過 |
| 500 | INTERNAL_ERROR | true | 内部エラー |
| 503 | REGISTRATION_BUSY | true | ロック競合（再試行可） |
| 503 | ID_SPACE_EXHAUSTED | false | ID 枯渇（再試行不可） |
| 503 | SERVICE_UNAVAILABLE | true | サービス利用不可 |

エラーボディ:
```json
{ "error": { "code": "INVALID_REQUEST", "retryable": false } }
```

## データモデル

### ID 採番器 (`id_allocator`)

| 列 | 型 | 説明 |
|---|---|---|
| singleton | SMALLINT | 常に 1（単一行保証） |
| next_id | INTEGER | 次に発行する ID（1〜16777216） |

### 登録 (`registrations`)

| 列 | 型 | 説明 |
|---|---|---|
| user_id | INTEGER | 個人 ID（1〜16777215、PK） |
| request_id | UUID | クライアント発行の UUID v4（UNIQUE） |
| credential_hash | BYTEA | secret の SHA-256 ハッシュ（32 バイト、UNIQUE） |
| created_at | TIMESTAMPTZ | 登録日時 |
| state | TEXT | ACTIVE / RETIRED |

### ID 仕様

- 範囲: 1 〜 16,777,215（24bit の最大値）
- 0 は予約（発行しない）
- 再利用なし（枯渇時はサービス終了）
- 枯渇時: next_id = 16777216 になると 503 ID_SPACE_EXHAUSTED

## ローカル開発

```bash
# Docker Compose で起動（PostgreSQL + アプリ）
docker compose up -d

# テスト（Testcontainers 使用）
docker run --rm -v "$PWD":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock \
  maven:3.9-eclipse-temurin-21 mvn -B verify
```

## デプロイ済み環境

| 項目 | 値 |
|---|---|
| Cloud Run URL | https://id-server-1084526017972.asia-northeast1.run.app |
| Cloud Run サービス | `id-server`（asia-northeast1, revision `id-server-00004-ddk`） |
| Cloud SQL | `crosspath-pg`（POSTGRES_16, db-f1-micro, asia-northeast1-a） |
| 稼働確認 | `/actuator/health` = 200 UP、登録 201 → 再送 200（同一 user_id） |

## GCP デプロイ手順

`deploy/` ディレクトリのスクリプトを番号順に実行する（ただし `20-create-sql.sh` / `30-secret.sh` は冪等。
既存リソースを検出して安全にスキップする）。詳細は `deploy/README.md` 参照。

1. `10-enable-apis.sh` - GCP API 有効化
2. `20-create-sql.sh` - Cloud SQL インスタンス・DB・ユーザ作成（冪等）
3. `30-secret.sh` - Secret Manager にパスワードを保存（冪等）
4. `40-build-push.sh` - Artifact Registry にイメージを build & push
5. `50-deploy-cloudrun.sh` - Cloud Run にデプロイ
6. `99-smoke-test.sh` - デプロイ後の動作確認

コスト削減のため、使用しない期間は `61-sql-stop.sh` で SQL を停止、
再開時は `60-sql-start.sh` で起動する。

### Cloud SQL 接続方式（実装上の決定）

Cloud Run から Cloud SQL へは **Cloud SQL Java Connector**
(`com.google.cloud.sql:postgres-socket-factory`, `pom.xml` の runtime 依存) で接続する。

```
jdbc:postgresql:///<DB>?cloudSqlInstance=<PROJECT>:<REGION>:<INSTANCE>
  &socketFactory=com.google.cloud.sql.postgres.SocketFactory&ipType=PUBLIC&socketTimeout=15&connectTimeout=10
```

理由: pgjdbc は `/cloudsql/<INSTANCE>` の Unix ドメインソケットを解釈せず TCP（host=null）に
フォールバックして起動に失敗するため（2026-09-26 に検証済み）。Connector は Cloud SQL Admin API 経由で
TLS 接続を確立するので `/cloudsql` のマウントに依存しない。

`--add-cloudsql-instances` は互換のため付与しているが、接続には必須ではない。

### 必要な IAM 権限（Cloud Run 実行サービスアカウント）

既定の Compute Engine SA（`<PROJECT_NUMBER>-compute@developer.gserviceaccount.com`）に以下を付与する
（未付与だと revision 作成が `Permission denied on secret` や接続失敗で落ちる）:

- `roles/secretmanager.secretAccessor`（秘密 `db-app-password`, `db-owner-password` に対して）
- `roles/cloudsql.client`（プロジェクトに対して）

```bash
SA="$(gcloud projects describe ${PROJECT_ID} --format='value(projectNumber)')-compute@developer.gserviceaccount.com"
for s in db-app-password db-owner-password; do
  gcloud secrets add-iam-policy-binding $s --member="serviceAccount:$SA" \
    --role=roles/secretmanager.secretAccessor
 done
gcloud projects add-iam-policy-binding ${PROJECT_ID} \
  --member="serviceAccount:$SA" --role=roles/cloudsql.client
```

### レート制限の上書き

`RATE_LIMIT_ENABLED` / `RATE_LIMIT_PER_IP_PER_MINUTE` / `RATE_LIMIT_PER_IP_BURST` /
`RATE_LIMIT_NEW_REGISTRATIONS_PER_SECOND` で上書きできる（既定: true, 10, 20, 10）。
大量の並行リクエストを検証する場合は一時的に `RATE_LIMIT_ENABLED=false` の revision を
別サービスとして立てると、機能検証をレート制限にマスクされずに実施できる。

### DB 権限付与

アプリロール `crosspath_app` への DB 権限付与は Flyway V2 マイグレーション
(`src/main/resources/db/migration/V2__grants.sql`) で行う。
マイグレーションユーザー（`DB_MIGRATION_USER`）には Cloud SQL 組み込みの所有者ロール
`postgres` を設定し、アプリ実行ユーザー（`DB_USER`）には権限を絞った `crosspath_app` を設定する。

### 必要な環境変数

各スクリプトは以下の環境変数で上書き可能（既定値あり）。スクリプト `20-create-sql.sh` と
`30-secret.sh` は `/tmp/` 経由でパスワードを受け渡す。シークレット名は
`db-owner-password`（postgres 用）と `db-app-password`（crosspath_app 用）。

| 変数名 | 既定値 | 説明 |
|---|---|---|
| `PROJECT_ID` | `crosspath-id-server` | GCP プロジェクト ID |
| `REGION` | `asia-northeast1` | GCP リージョン |
| `SQL_INSTANCE` | `crosspath-pg` | Cloud SQL インスタンス名 |
| `DB_NAME` | `crosspath` | データベース名 |
| `APP_USER` | `crosspath_app` | アプリ実行 DB ユーザ |
| `OWNER_USER` | `postgres` | マイグレーション実行ユーザ（Cloud SQL 組み込み） |
| `DB_MIGRATION_USER` | `postgres` | Flyway マイグレーションユーザ（`50-deploy-cloudrun.sh` で使用） |
| `DB_USER` | `crosspath_app` | アプリ DB ユーザ（`50-deploy-cloudrun.sh` で使用） |
| `AR_REPO` | `crosspath` | Artifact Registry リポジトリ名 |
| `SERVICE` | `id-server` | Cloud Run サービス名 |

## コスト

| リソース | 構成 | 月額（概算） |
|---|---|---|
| Cloud SQL | db-f1-micro, 10GB HDD, 常時稼働 | ~$8-10/月 |
| Cloud SQL | 停止時（ストレージのみ） | ~$1/月 |
| Cloud Run | 512MB, min=0 | 無料枠内（$0~） |
| Artifact Registry | 小規模 | ~$0.1/月 |
| **合計（常時稼働）** | | **~$8-11/月** |
| **合計（SQL 停止時）** | | **~$1/月** |

普段使わないときは `deploy/61-sql-stop.sh` で Cloud SQL を停止推奨。

## セキュリティ・運用上の制約

- **署名なし**: secret はクライアント側で生成した乱数。本人性は未検証（アプリの初回起動毎に新規 secret 生成を想定）。
- **バックアップ巻き戻し禁止**: 過去のバックアップに戻すと ID 重複が発生する。リストアは全台帳消去＋再採番が必要。
- **複数インスタンス時のレート制限**: レート制限はインメモリ実装。複数インスタンスで動作させる場合は共有ストア（Redis 等）が必要。
- **1 ID 1 端末**: secret を共有しないこと。ID 復旧機能はない（端末紛失時は新規登録、前の ID は使えない）。

## 試験対応表

| テストクラス | 設計書テストケース | 内容 |
|---|---|---|
| `S01ConcurrentRegistrationsTest` | S01 | 100 並行新規登録で ID 一意 |
| `S02ConcurrentDuplicateRequestTest` | S02 | 100 並行重複要求で 1 行のみ |
| `S03ConflictRequestIdTest` | S03 | 別 secret 同一 request_id → 409 |
| `S04ConflictSecretTest` | S04 | 同一 secret 別 request_id → 409 |
| `S05RetrySameRequestTest` | S05 | 再送で 200 かつ同一ボディ |
| `S08IdSpaceExhaustedTest` | S08 | 枯渇動作（最終 ID → 503） |
| `S09CheckConstraintTest` | S09 | CHECK 制約違反 |
| `S10RetiredStateTest` | S10 | RETIRED → 410 |
| `S14LockTimeoutTest` | S14 | ロックタイムアウト → 503 |
| `S17InvalidInputTest` | S17 | 不正入力 → 400/401 |

## ライセンス

MIT License

Copyright (c) 2025 goshieta