#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-crosspath-id-server}"
SQL_INSTANCE="${SQL_INSTANCE:-crosspath-pg}"

echo "Starting Cloud SQL instance..."
gcloud sql instances patch "${SQL_INSTANCE}" \
    --project="${PROJECT_ID}" \
    --activation-policy=ALWAYS

echo "Instance started."