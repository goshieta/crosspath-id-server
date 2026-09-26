#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-crosspath-id-server}"

echo "Storing database passwords in Secret Manager..."

# 既存のシークレットがなければ作成する（冪等）
if ! gcloud secrets describe db-app-password --project="${PROJECT_ID}" >/dev/null 2>&1; then
    gcloud secrets create db-app-password --project="${PROJECT_ID}" \
        --labels="app=crosspath-id-server,type=db-app-password"
fi

if ! gcloud secrets describe db-owner-password --project="${PROJECT_ID}" >/dev/null 2>&1; then
    gcloud secrets create db-owner-password --project="${PROJECT_ID}" \
        --labels="app=crosspath-id-server,type=db-owner-password"
fi

# パスワードファイルが存在する場合は Secret Manager に追加
if [ -f /tmp/app-password.txt ]; then
    cat /tmp/app-password.txt | gcloud secrets versions add db-app-password \
        --project="${PROJECT_ID}" --data-file=-
    echo "db-app-password updated"
fi

if [ -f /tmp/owner-password.txt ]; then
    cat /tmp/owner-password.txt | gcloud secrets versions add db-owner-password \
        --project="${PROJECT_ID}" --data-file=-
    echo "db-owner-password updated"
fi

echo "Secrets stored successfully."