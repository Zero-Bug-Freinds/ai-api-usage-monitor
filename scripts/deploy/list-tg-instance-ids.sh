#!/usr/bin/env bash
# Print comma-separated EC2 instance IDs registered to an ALB target group (Id values starting with i-).
# Used by Release auto-roll and Deploy when instance IDs are omitted.
#
# Required env: TARGET_GROUP_ARN
set -euo pipefail

TARGET_GROUP_ARN="${TARGET_GROUP_ARN:?}"

raw="$(aws elbv2 describe-target-health \
  --target-group-arn "$TARGET_GROUP_ARN" \
  --query 'TargetHealthDescriptions[].Target.Id' \
  --output text)"

[[ -z "${raw//[$' \t']}" ]] && exit 0

IFS=$'\t' read -r -a ids <<< "$raw" || true

csv=""
for id in "${ids[@]}"; do
  id="$(echo "$id" | xargs)"
  [[ "$id" =~ ^i- ]] || continue
  [[ -n "$csv" ]] && csv+=","
  csv+="$id"
done

echo -n "$csv"
