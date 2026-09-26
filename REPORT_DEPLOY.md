# REPORT_DEPLOY.md — GCP デプロイ実施報告（2026-09-26）

## 1. 結果サマリ

| 項目 | 状態 |
|---|---|
| GitHub push | ✅ `goshieta/crosspath-id-server` `main` |
| コンテナイメージ | ✅ `asia-northeast1-docker.pkg.dev/crosspath-id-server/crosspath/id-server:latest` |
| Cloud Run デプロイ | ✅ `id-server`（revision `id-server-00004-ddk`、100% トラフィック） |
| 公開 URL | https://id-server-1084526017972.asia-northeast1.run.app |
| Cloud SQL 接続 | ✅ Flyway V1/V2 適用・登録/参照が実 DB で動作 |
| 機能検証（第三者スクリプト） | ✅ **25 PASS / 0 FAIL** |
| ユニット/統合テスト | ✅ 47 tests, 0 failures（`mvn -B verify`） |

## 2. 作業前の状態と実施した是正

初期状態は「GCP リソースが半分だけ作成済み」で、そのままでは起動しない状態だった。

| 検出した問題 | 実体 | 対応 |
|---|---|---|
| DB ユーザの不整合 | Secret は `db-owner-password` / `db-app-password` に存在するが、オーナーユーザ `crosspath` は未作成（存在するのは `postgres` と `crosspath_app` のみ） | オーナーを Cloud SQL 組み込みの `postgres` に確定し、両ユーザのパスワードを Secret の値へ `set-password` で一致させた |
| `50-deploy-cloudrun.sh` の既定値 | `OWNER_USER` 既定が `crosspath`（存在しないユーザ） | 既定を `postgres` に修正 |
| `DB_URL` のエスケープ | ダブルクォート内の `\&` がバックスラッシュごと環境変数に入る | エスケープを除去 |
| `99-smoke-test.sh` | `check [ ... ] && [ ... ]` という誤った呼び出しで整数比較エラー | `RC` 変数方式に修正（7 箇所） |
| `20-create-sql.sh` の再実行性 | インスタンス/DB/ユーザ作成が非冪等 | describe ガード + `set-password` で冪等化 |
| IAM 不足 | revision 作成が `Permission denied on secret` で失敗 | 実行 SA に `secretmanager.secretAccessor` / `cloudsql.client` を付与 |
| Cloud SQL 接続方式 | `/cloudsql/<INSTANCE>` が**空**でマウントされておらず、pgjdbc は Unix ソケットを解釈せず TCP（host=null）へフォールバック → 起動失敗 | **Cloud SQL Java Connector**（`postgres-socket-factory 1.30.0`）へ切替 |
| `PORT` / `SERVER_PORT` の暗黙一致 | Cloud Run は `PORT` を注入するがアプリは `SERVER_PORT` を参照（既定 8080 で偶然一致） | デプロイ時に `SERVER_PORT=8080` を明示 |
| レート制限の上書き | SPEC §4 の環境変数名（`RATE_LIMIT_*`）で上書きできない | `application.yml` に `app.rate-limit.*` の env マッピングを追加 |
| Dockerfile の no-op フラグ | `-Dspring.boot.maven-plugin.skip=true`（正しい名前は `spring-boot.repackage.skip`）で無視されていた | フラグを削除 |

## 3. 検証エビデンス

### 3.1 機能検証（制限オフの一時サービスに対して）

レート制限（既定 10/分/IP）は本番仕様として維持しつつ、機能を制限にマスクされず検証するため、
同一イメージで `RATE_LIMIT_ENABLED=false` の一時サービスを立てて第三者スクリプトを実行した。

```
================ 結果: PASS=25 FAIL=0 ================
```

検証項目: 到達性 / health / 新規登録 50 並行 / 同一 request_id 再送 20 並行 /
request_id 衝突 409 / secret 衝突 409 / GET me / 不正入力（壊れた JSON・不正 secret・
UUID v1・1KiB 超・未知フィールド）/ Cache-Control: no-store / 未知パス 404 / 未対応メソッド 405

検証後、当該一時サービスは削除済み（未認証 + 制限オフを残さないため）。

### 3.2 本番サービス（レート制限あり）

```
GET  /actuator/health            -> 200 {"status":"UP"}
POST /v1/registrations           -> 201 {"user_id":66, ...}
POST /v1/registrations (再送)     -> 200 {"user_id":66, ...}   # 同一値
GET  /v1/registrations/me        -> 200 {"user_id":66, ...}   # 同一値
```

同一 IP からの連続リクエストでは 429（`RATE_LIMITED`）が返ることを確認済み
（レート制限が本番で有効に動作している証跡）。

## 4. 未対応・申し送り

1. **アプリロールの権限は V2 マイグレーション依存**: マイグレーション実行ユーザは `postgres`（スーパーユーザ相当）。
   厳密な最小権限運用にしたい場合は、所有者ロールを別途作成して `ALTER DATABASE ... OWNER` を実施する必要がある
   （Cloud SQL の `gcloud sql databases create` に owner 指定がないため、SQL 実行手段が必要）。
2. **Cloud SQL は常時稼働で月 ~$8-10**: 未使用期間は `deploy/61-sql-stop.sh` で停止（ストレージ課金のみ ~$1/月）。
3. **本番レート制限は 10 回/分/IP**。モバイル網の CGNAT 共有 IP では複数端末が同一 IP になり得るため、
   実運用で 429 が増える場合は `RATE_LIMIT_PER_IP_PER_MINUTE` を上げて調整する。
4. **`--allow-unauthenticated` で公開**している。API 自体は registration_secret による認証を持つが、
   エンドポイントは誰でも到達可能。必要なら Cloud Armor / API キー導入を検討。
