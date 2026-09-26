# REPORT_DOCS.md

## (1) 変更点一覧

### README.md

| 行・箇所 | 変更内容 |
|---|---|
| デプロイ手順の冒頭 | `20-create-sql.sh` `30-secret.sh` が冪等であることを追記 |
| デプロイ手順の表 | 各スクリプトの説明に冪等性を明記。`60-sql-start.sh` / `61-sql-stop.sh` とその用途を追記 |
| DB 権限付与の節 | `postgres` が Cloud SQL 組み込みの所有者ロールであることを明記 |
| 必要な環境変数 | 表形式に変更し、`OWNER_USER=postgres` に修正。`DB_MIGRATION_USER` / `DB_USER` / シークレット名 (`db-owner-password`, `db-app-password`) と `/tmp/` 経由のパスワード受け渡しを追加 |

### deploy/README.md

| 行・箇所 | 変更内容 |
|---|---|
| デプロイ手順の表 | `20-create-sql.sh` に「冪等」、`30-secret.sh` に「冪等」を追記。`60-sql-start.sh` の説明を「SQL 起動」→「Cloud SQL 起動」に統一 |
| 権限付与の節 | 所有者ロールを `crosspath` から `postgres` に修正。「Cloud SQL 組み込みの所有者ロール」と明記 |
| **新設: シークレット管理** | `30-secret.sh` の動作説明、シークレット名 (`db-owner-password`, `db-app-password`) と用途の表を追加。冪等性の説明を追記 |
| ロールバック | `--project=` フラグ追加。イメージタグ確認コマンドを具体化。DB スキーマロールバックの方針（原則しない、やむを得ない場合は全消去＋再作成）を追記 |

---

## (2) Dockerfile 監査の指摘

監査対象: `Dockerfile`（変更なし・指摘のみ）

### 指摘1: `-Dspring.boot.maven-plugin.skip=true` が誤ったプロパティ名 [重要度: 中]

- **該当行**: `RUN mvn -B -DskipTests package -Dspring.boot.maven-plugin.skip=true`
- **問題**: Spring Boot Maven Plugin の `skip` パラメータの正しいプロパティ名は `spring-boot.repackage.skip`（または `skip`）。`spring.boot.maven-plugin.skip` は認識されず、フラグは無視される。
- **結果**: 現状は無視されるためビルドは正常に動作し、fat JAR が生成される。ただし意図が不明瞭であり、将来のリファクタリングで誤って有効化されるリスクがある。
- **推奨対応**: フラグ自体を削除する（`spring-boot-maven-plugin` による repackage は `package` フェーズで必要）。または正しいプロパティ名 `-Dspring-boot.repackage.skip=true` を使うが、そもそも Dockerfile のビルドステージで skip する理由がない。

### 指摘2: `PORT` 環境変数と `SERVER_PORT` の不一致 [重要度: 中〜高]

- **該当行**: `EXPOSE 8080`（ドキュメンテーションのみ → 問題なし）／ `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]`（起動コマンド）
- **問題**: Cloud Run はコンテナに `PORT` 環境変数を注入する（`50-deploy-cloudrun.sh` で `--port=8080` を指定しているため `PORT=8080`）。しかしアプリケーションは `application.yml` で `server.port: ${SERVER_PORT:8080}` と定義しており、`PORT` ではなく `SERVER_PORT` を参照する。
- **結果**: 現状は `SERVER_PORT` の既定値が 8080 であり、Cloud Run の `PORT` も 8080 なので偶然動作する。しかし両環境変数の意味が異なる（`PORT` vs `SERVER_PORT`）ため、将来 `--port=9090` に変更すると `PORT=9090` になるがアプリは既定の 8080 で待受ける → ヘルスチェック失敗。
- **推奨対応**（変更不可のため参考）:
  - パターンA: `application.yml` で `server.port: ${PORT:8080}` に変更する
  - パターンB: `50-deploy-cloudrun.sh` の `--set-env-vars` に `SERVER_PORT=${PORT}` を追加する

### 指摘3: マルチステージ構成 [問題なし]

- `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine` の構成は妥当。
- `-XX:MaxRAMPercentage=75`（512MiB の 75% = 384MiB）は Spring Boot アプリに適正。
- 非 root ユーザ (`appuser`) で実行するのは Cloud Run のセキュリティベストプラクティスに合致。

### 指摘4: Flyway migration SQL が jar に含まれるか [問題なし]

- `src/main/resources/db/migration/` 配下の `V1__init.sql` / `V2__grants.sql` は Maven 標準の resource 配置であり、fat JAR の `BOOT-INF/classes/db/migration/` に格納される。
- `spring.flyway.locations=classpath:db/migration` が正しく参照する。

### 指摘5: HEALTHCHECK 命令 [問題なし]

- Dockerfile に `HEALTHCHECK` は未記述だが、Cloud Run は独自に `/actuator/health` エンドポイトへの HTTP リクエストでヘルスチェックを行うため不要。
- `application.yml` で `management.endpoint.health.probes.enabled: true` により Kubernetes 向け probes も有効だが Cloud Run では使われない。悪影響はない。

### 指摘6: イメージサイズ [問題なし]

- `eclipse-temurin:21-jre-alpine` ベースで、依存関係のキャッシュは `dependency:go-offline` で Docker レイヤー分離されている。適切な設計。

---

## (3) 未解決の懸念

1. **PORT vs SERVER_PORT 問題**: 現状は既定値の一致で偶然動いているだけ。デプロイ後に Cloud Run の `PORT` とアプリの `SERVER_PORT` が一致しているか確認する必要がある（`/actuator/health` が 200 を返せば OK）。将来のメンテナンス時に破綻しやすい。
2. **`spring.boot.maven-plugin.skip` のゴミフラグ**: ビルドには影響していないが、コードの意図が不明瞭。次の人が見たときに混乱する可能性がある。
3. **デプロイ未完了**: 本番デプロイは未実施。ドキュメント上の URL や実際の接続確認は行われていない。デプロイ後に `99-smoke-test.sh` を実行してエンドツーエンドを確認すること。
4. **最小権限の妥当性**: `V2__grants.sql` の権限（registrations: SELECT/INSERT, id_allocator: SELECT/UPDATE）は現状の API 仕様に合致しているが、将来 `/v1/registrations/me` で GET のみのエンドポイトが必要なら SELECT のみの別ロールを検討してもよい（ただし現状 crosspath_app は SELECT も持っているため問題ない）。