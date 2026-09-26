# deploy/README.md

## デプロイ手順

以下のスクリプトを番号順に実行する。

| 順番 | スクリプト | 内容 |
|---|---|---|
| 1 | `10-enable-apis.sh` | GCP API を有効化 |
| 2 | `20-create-sql.sh` | Cloud SQL PostgreSQL 16 作成、ユーザー作成 |
| 3 | `30-secret.sh` | Secret Manager にパスワード保存 |
| 4 | `40-build-push.sh` | Artifact Registry にイメージを build & push |
| 5 | `50-deploy-cloudrun.sh` | Cloud Run にデプロイ |
| - | `60-sql-start.sh` | SQL 起動（コスト削減用） |
| - | `61-sql-stop.sh` | SQL 停止（コスト削減用） |
| 6 | `99-smoke-test.sh` | デプロイ後の動作確認 |

## 権限付与について

アプリロール `crosspath_app` への DB 権限付与は Flyway V2 マイグレーション
(`V2__grants.sql`) で行う。`DB_MIGRATION_USER`/`DB_MIGRATION_PASSWORD` には
所有者ロール（`crosspath`）を設定し、マイグレーション時に権限付与が実行される。

`deploy/sql/grants.sql` は手動実行時の代替として維持。

## コスト（2025年現在の目安）

| リソース | 構成 | 月額（概算） |
|---|---|---|
| Cloud SQL | db-f1-micro, 10GB HDD, 常時稼働 | ~$8-10/月 |
| Cloud SQL | db-f1-micro, 10GB HDD, 停止時 | ~$1/月（ストレージのみ） |
| Cloud Run | 512MB, CPU 1, min=0 | 無料枠内（$0~） |
| Artifact Registry | 小規模 | ~$0.1/月 |

合計: ~$8-11/月（常時稼働）、~$1/月（SQL 停止時）

### 起動 / 停止 運用

普段使わないときは `61-sql-stop.sh` で Cloud SQL を停止し、使う前に `60-sql-start.sh` で起動する。
停止中もストレージ課金（約 $1/月）は発生する。

## ロールバック

```bash
gcloud run deploy id-server --region=asia-northeast1 --image=<前のイメージタグ>
```

前のイメージタグは `gcloud artifacts docker images list ...` で確認する。

## 必要な権限

デプロイ実行アカウントに必要な IAM 権限:
- `roles/run.admin`
- `roles/cloudbuild.builds.editor`
- `roles/artifactregistry.admin`
- `roles/secretmanager.admin`
- `roles/cloudsql.admin`
- `roles/iam.serviceAccountUser`