#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-crosspath-id-server}"
REGION="${REGION:-asia-northeast1}"

echo "Enabling required APIs..."
gcloud services enable run.googleapis.com \
    sqladmin.googleapis.com \
    artifactregistry.googleapis.com \
    cloudbuild.googleapis.com \
    secretmanager.googleapis.com \
    --project="${PROJECT_ID}"

echo "APIs enabled: run, sqladmin, artifactregistry, cloudbuild, secretmanager"