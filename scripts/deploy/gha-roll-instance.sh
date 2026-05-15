#!/usr/bin/env bash
# GitHub Actions helper: drain one EC2 from an ALB Target Group, SSM deploy, re-register.
# See `docs/aws-github-oidc-ecr-ssm.md` and `.github/workflows/deploy.yml`.
#
# Required env:
#   AWS_REGION
#   TARGET_GROUP_ARN
#   INSTANCE_ID
#   DEPLOY_SHA       — value for IMAGE_TAG on the instance
# Optional env:
#   SSM_DEPLOY_ROOT — default /opt/ai-api-usage-monitor (repo root; align with `terraform output ssm_deploy_root_default`)
#   TARGET_PORT     — default 8888 (ALB register/deregister port; match `terraform output alb_target_port`)

# Default before nounset so empty/unset never trips strict mode (matches deploy docs / Terraform user-data).
export SSM_DEPLOY_ROOT="${SSM_DEPLOY_ROOT:-/opt/ai-api-usage-monitor}"

set -euo pipefail

TARGET_PORT="${TARGET_PORT:-8888}"
DRAIN_WAIT_SEC="${DRAIN_WAIT_SEC:-300}"

INSTANCE_ID="${INSTANCE_ID:?}"
TARGET_GROUP_ARN="${TARGET_GROUP_ARN:?}"
DEPLOY_SHA="${DEPLOY_SHA:?}"

echo "Deregister $INSTANCE_ID:$TARGET_PORT from $TARGET_GROUP_ARN"
aws elbv2 deregister-targets \
  --target-group-arn "$TARGET_GROUP_ARN" \
  --targets "Id=$INSTANCE_ID,Port=$TARGET_PORT"

echo "Wait for draining (up to ${DRAIN_WAIT_SEC}s)"
deadline=$((SECONDS + DRAIN_WAIT_SEC))
while (( SECONDS < deadline )); do
  state="$(aws elbv2 describe-target-health \
    --target-group-arn "$TARGET_GROUP_ARN" \
    --targets "Id=$INSTANCE_ID,Port=$TARGET_PORT" \
    --query 'TargetHealthDescriptions[0].TargetHealth.State' --output text 2>/dev/null || true)"
  if [[ "$state" == "unused" ]] || [[ "$state" == "None" ]] || [[ "$state" == "null" ]] || [[ "$state" == "" ]]; then
    break
  fi
  sleep 5
done

PARAMS_FILE="$(mktemp)"
# JSON for AWS-RunShellScript — keep commands as a single logical deploy (SSM prints each line).
jq -n \
  --arg sha "$DEPLOY_SHA" \
  --arg region "$AWS_REGION" \
  --arg root "$SSM_DEPLOY_ROOT" \
  '{commands:[
      "set -euo pipefail",
      ("export IMAGE_TAG=" + $sha),
      ("export AWS_REGION=" + $region),
      ("export DEPLOY_ROOT=\"" + $root + "\""),
      ("cd \"" + $root + "\""),
      "if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then git fetch origin \"" + $sha + "\" 2>/dev/null || git fetch --depth 300 origin || true; git checkout -f \"" + $sha + "\" 2>/dev/null || true; fi",
      "test -f scripts/deploy/on-instance-compose-roll.sh || { echo \"Missing scripts/deploy/on-instance-compose-roll.sh under deploy root; bootstrap the host (Terraform user-data git clone) or clone the repo to SSM_DEPLOY_ROOT.\"; exit 1; }",
      "chmod +x scripts/deploy/on-instance-compose-roll.sh scripts/deploy/docker-compose-prod.sh scripts/deploy/validate-env-deploy.sh 2>/dev/null || true",
      "bash scripts/deploy/on-instance-compose-roll.sh"
    ]}' >"$PARAMS_FILE"

echo "SSM SendCommand to $INSTANCE_ID"
CMD_ID="$(aws ssm send-command \
  --instance-ids "$INSTANCE_ID" \
  --document-name "AWS-RunShellScript" \
  --comment "compose deploy ${DEPLOY_SHA}" \
  --parameters "file://${PARAMS_FILE}" \
  --query Command.CommandId --output text)"
rm -f "$PARAMS_FILE"

deadline=$((SECONDS + 900))
while (( SECONDS < deadline )); do
  STATUS="$(aws ssm get-command-invocation \
    --command-id "$CMD_ID" \
    --instance-id "$INSTANCE_ID" \
    --query Status --output text 2>/dev/null || true)"
  if [[ "$STATUS" == "Success" ]]; then
    break
  fi
  if [[ "$STATUS" == "Failed" ]] || [[ "$STATUS" == "Cancelled" ]] || [[ "$STATUS" == "TimedOut" ]]; then
    echo "SSM command failed: $STATUS" >&2
    aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" >&2 || true
    exit 1
  fi
  sleep 5
done

STATUS="$(aws ssm get-command-invocation \
  --command-id "$CMD_ID" \
  --instance-id "$INSTANCE_ID" \
  --query Status --output text)"
if [[ "$STATUS" != "Success" ]]; then
  echo "SSM command did not succeed (last status=$STATUS)" >&2
  exit 1
fi

echo "Register $INSTANCE_ID:$TARGET_PORT back to $TARGET_GROUP_ARN"
aws elbv2 register-targets \
  --target-group-arn "$TARGET_GROUP_ARN" \
  --targets "Id=$INSTANCE_ID,Port=$TARGET_PORT"

echo "Done $INSTANCE_ID"
