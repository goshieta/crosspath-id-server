#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-crosspath-id-server}"
REGION="${REGION:-asia-northeast1}"
SQL_INSTANCE="${SQL_INSTANCE:-crosspath-pg}"
DB_NAME="${DB_NAME:-crosspath}"
AR_REPO="${AR_REPO:-crosspath}"
SERVICE="${SERVICE:-id-server}"

IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPO}/id-server:latest"
# Cloud SQL Java Connector 経由で接続（/cloudsql ソケットに依存しない）
DB_URL="jdbc:postgresql:///${DB_NAME}?cloudSqlInstance=${PROJECT_ID}:${REGION}:${SQL_INSTANCE}&socketFactory=com.google.cloud.sql.postgres.SocketFactory&ipType=PUBLIC&socketTimeout=15&connectTimeout=10"

echo "Deploying to Cloud Run..."
gcloud run deploy "${SERVICE}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --image="${IMAGE}" \
    --allow-unauthenticated \
    --port=8080 \
    --memory=512Mi \
    --cpu=1 \
    --min-instances=0 \
    --max-instances=2 \
    --concurrency=20 \
    --timeout=30 \
    --no-cpu-boost \
    --cpu-throttling \
    --add-cloudsql-instances="${PROJECT_ID}:${REGION}:${SQL_INSTANCE}" \
    --set-env-vars="DB_URL=${DB_URL},DB_USER=${APP_USER:-crosspath_app},DB_MIGRATION_USER=${OWNER_USER:-postgres},SERVER_PORT=8080" \
    --set-secrets="DB_PASSWORD=db-app-password:latest,DB_MIGRATION_PASSWORD=db-owner-password:latest"

echo "Deployment complete."
gcloud run services describe "${SERVICE}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --format="value(status.url)"