#!/usr/bin/env bash
# Create an ECR repository if it does not exist (idempotent).
# Usage: ensure-ecr-repository.sh <full-repository-name>
# Example: ensure-ecr-repository.sh ai-api-usage-monitor/api-gateway-service
set -euo pipefail

repo_name="${1:?repository name required}"
region="${AWS_REGION:?AWS_REGION required}"

if aws ecr describe-repositories --repository-names "$repo_name" --region "$region" >/dev/null 2>&1; then
  echo "ECR repository already exists: $repo_name"
else
  echo "Creating ECR repository: $repo_name"
  aws ecr create-repository \
    --repository-name "$repo_name" \
    --region "$region" \
    --image-scanning-configuration scanOnPush=true \
    --image-tag-mutability MUTABLE
fi
