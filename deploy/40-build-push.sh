#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-crosspath-id-server}"
REGION="${REGION:-asia-northeast1}"
AR_REPO="${AR_REPO:-crosspath}"

echo "Creating Artifact Registry repository (docker) if not exists..."
gcloud artifacts repositories describe "${AR_REPO}" \
    --project="${PROJECT_ID}" \
    --location="${REGION}" >/dev/null 2>&1 || \
    gcloud artifacts repositories create "${AR_REPO}" \
        --project="${PROJECT_ID}" \
        --repository-format=docker \
        --location="${REGION}"

echo "Building and pushing image via Cloud Build..."
gcloud builds submit \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --tag="${REGION}-docker.pkg.dev/${PROJECT_ID}/${AR_REPO}/id-server:latest" \
    --timeout=30m

echo "Build and push complete."