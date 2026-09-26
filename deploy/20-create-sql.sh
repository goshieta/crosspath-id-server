#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-crosspath-id-server}"
REGION="${REGION:-asia-northeast1}"
SQL_INSTANCE="${SQL_INSTANCE:-crosspath-pg}"
DB_NAME="${DB_NAME:-crosspath}"
APP_USER="${APP_USER:-crosspath_app}"
# マイグレーション実行ユーザー（テーブル所有者）。Cloud SQL 既定の postgres を使う。
OWNER_USER="${OWNER_USER:-postgres}"

if gcloud sql instances describe "${SQL_INSTANCE}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
    echo "Cloud SQL instance ${SQL_INSTANCE} already exists; skipping creation."
else
echo "Creating Cloud SQL PostgreSQL 16 instance (db-f1-micro, 10GB HDD)..."
gcloud sql instances create "${SQL_INSTANCE}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --database-version=POSTGRES_16 \
    --tier=db-f1-micro \
    --edition=enterprise \
    --storage-type=HDD \
    --storage-size=10GB \
    --no-backup=false \
    --backup-start-time=02:00 \
    --retained-backups-count=1 \
    --no-ha \
    --availability-type=zonal \
    --no-assign-ip \
    --async
fi

echo "Waiting for instance creation to complete..."
gcloud sql instances describe "${SQL_INSTANCE}" --project="${PROJECT_ID}" --format="value(state)"

# Generate random passwords
OWNER_PASSWORD=$(openssl rand -base64 24)
APP_PASSWORD=$(openssl rand -base64 24)

# 所有者ユーザーは既定の postgres を使う（作成はせずパスワードを設定・冪等）
echo "Setting password for owner user ${OWNER_USER}..."
gcloud sql users set-password "${OWNER_USER}" \
    --project="${PROJECT_ID}" \
    --instance="${SQL_INSTANCE}" \
    --password="${OWNER_PASSWORD}"

if gcloud sql users describe "${APP_USER}" --project="${PROJECT_ID}" \
        --instance="${SQL_INSTANCE}" >/dev/null 2>&1; then
    echo "App user ${APP_USER} already exists; setting password..."
else
    echo "Creating app user ${APP_USER}..."
    gcloud sql users create "${APP_USER}" \
        --project="${PROJECT_ID}" \
        --instance="${SQL_INSTANCE}" \
        --password="${APP_PASSWORD}"
fi
gcloud sql users set-password "${APP_USER}" \
    --project="${PROJECT_ID}" \
    --instance="${SQL_INSTANCE}" \
    --password="${APP_PASSWORD}"

if gcloud sql databases describe "${DB_NAME}" --project="${PROJECT_ID}" \
        --instance="${SQL_INSTANCE}" >/dev/null 2>&1; then
    echo "Database ${DB_NAME} already exists; skipping creation."
else
    echo "Creating database ${DB_NAME}..."
    gcloud sql databases create "${DB_NAME}" \
        --project="${PROJECT_ID}" \
        --instance="${SQL_INSTANCE}"
fi

echo "Applying grants.sql as owner (fallback if V2 migration not used)..."
echo "Note: The preferred approach is to set DB_MIGRATION_USER/DB_MIGRATION_PASSWORD to the"
echo "      owner role and rely on V2__grants.sql migration."
echo "      If using direct SQL, run from Cloud Shell or proxy:"
echo "  gcloud sql connect ${SQL_INSTANCE} --user=${OWNER_USER} --database=${DB_NAME} < deploy/sql/grants.sql"

# Store passwords temporarily for next script
echo "${OWNER_PASSWORD}" > /tmp/owner-password.txt
echo "${APP_PASSWORD}" > /tmp/app-password.txt

echo "Cloud SQL instance created: ${SQL_INSTANCE}"
echo "Owner password and app password saved to /tmp/ for next step."