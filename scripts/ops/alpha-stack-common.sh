#!/usr/bin/env bash
# Shared helpers for alpha Method A: stop/start EC2 + RDS without terraform destroy.
# Requires: AWS CLI, terraform (for output discovery), IAM ec2/rds/autoscaling/elbv2 read+write.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TF_DIR="${TF_DIR:-${REPO_ROOT}/infra/terraform}"
AWS_REGION="${AWS_REGION:-}"
WAIT_TIMEOUT_SEC="${WAIT_TIMEOUT_SEC:-1800}"

tf_raw() {
  terraform -chdir="$TF_DIR" output -raw "$1" 2>/dev/null || true
}

resolve_aws_region() {
  if [[ -n "${AWS_REGION:-}" ]]; then
    printf '%s' "$AWS_REGION"
    return 0
  fi
  local from_tf
  from_tf="$(tf_raw aws_region)"
  if [[ -n "$from_tf" ]]; then
    printf '%s' "$from_tf"
    return 0
  fi
  local from_cli
  from_cli="$(aws configure get region 2>/dev/null || true)"
  if [[ -n "$from_cli" ]]; then
    printf '%s' "$from_cli"
    return 0
  fi
  echo "Set AWS_REGION or configure the AWS CLI default region." >&2
  return 1
}

resolve_rds_identifier() {
  if [[ -n "${RDS_INSTANCE_ID:-}" ]]; then
    printf '%s' "$RDS_INSTANCE_ID"
    return 0
  fi
  local id
  id="$(tf_raw staging_rds_identifier)"
  if [[ -n "$id" ]]; then
    printf '%s' "$id"
    return 0
  fi
  echo "Set RDS_INSTANCE_ID or run terraform apply with enable_staging_rds." >&2
  return 1
}

resolve_target_group_arn() {
  if [[ -n "${ALB_TARGET_GROUP_ARN:-}" ]]; then
    printf '%s' "$ALB_TARGET_GROUP_ARN"
    return 0
  fi
  local arn
  arn="$(tf_raw alb_target_group_arn)"
  if [[ -n "$arn" ]]; then
    printf '%s' "$arn"
    return 0
  fi
  echo "Set ALB_TARGET_GROUP_ARN or enable_compute_stack and terraform apply." >&2
  return 1
}

resolve_asg_name() {
  if [[ -n "${ASG_NAME:-}" ]]; then
    printf '%s' "$ASG_NAME"
    return 0
  fi
  local name
  name="$(tf_raw compute_asg_name)"
  if [[ -n "$name" ]]; then
    printf '%s' "$name"
    return 0
  fi
  echo "Set ASG_NAME or enable_compute_stack and terraform apply." >&2
  return 1
}

# Prefer TG-registered instance (ALB path); fall back to ASG instance list.
resolve_ec2_instance_id() {
  if [[ -n "${EC2_INSTANCE_ID:-}" ]]; then
    printf '%s' "$EC2_INSTANCE_ID"
    return 0
  fi
  local tg_arn
  tg_arn="$(resolve_target_group_arn)"
  local raw id csv=""
  raw="$(aws elbv2 describe-target-health \
    --region "$AWS_REGION" \
    --target-group-arn "$tg_arn" \
    --query 'TargetHealthDescriptions[].Target.Id' \
    --output text 2>/dev/null || true)"
  if [[ -n "${raw//[$' \t']}" ]]; then
    IFS=$'\t' read -r -a ids <<< "$raw" || true
    for id in "${ids[@]}"; do
      id="$(echo "$id" | xargs)"
      [[ "$id" =~ ^i- ]] || continue
      printf '%s' "$id"
      return 0
    done
  fi
  local asg
  asg="$(resolve_asg_name)"
  id="$(aws autoscaling describe-auto-scaling-groups \
    --region "$AWS_REGION" \
    --auto-scaling-group-names "$asg" \
    --query 'AutoScalingGroups[0].Instances[?LifecycleState==`InService`].InstanceId | [0]' \
    --output text 2>/dev/null || true)"
  if [[ -n "$id" && "$id" != "None" ]]; then
    printf '%s' "$id"
    return 0
  fi
  id="$(aws autoscaling describe-auto-scaling-groups \
    --region "$AWS_REGION" \
    --auto-scaling-group-names "$asg" \
    --query 'AutoScalingGroups[0].Instances[0].InstanceId' \
    --output text 2>/dev/null || true)"
  if [[ -n "$id" && "$id" != "None" ]]; then
    printf '%s' "$id"
    return 0
  fi
  echo "No EC2 instance found in target group or ASG $asg" >&2
  return 1
}

load_alpha_context() {
  AWS_REGION="$(resolve_aws_region)"
  export AWS_REGION
  RDS_INSTANCE_ID="$(resolve_rds_identifier)"
  export RDS_INSTANCE_ID
  EC2_INSTANCE_ID="$(resolve_ec2_instance_id)"
  export EC2_INSTANCE_ID
  ALB_TARGET_GROUP_ARN="$(resolve_target_group_arn)"
  export ALB_TARGET_GROUP_ARN
}

rds_status() {
  aws rds describe-db-instances \
    --region "$AWS_REGION" \
    --db-instance-identifier "$RDS_INSTANCE_ID" \
    --query 'DBInstances[0].DBInstanceStatus' \
    --output text
}

ec2_state() {
  aws ec2 describe-instances \
    --region "$AWS_REGION" \
    --instance-ids "$EC2_INSTANCE_ID" \
    --query 'Reservations[0].Instances[0].State.Name' \
    --output text
}

wait_rds_status() {
  local want="$1"
  local deadline=$((SECONDS + WAIT_TIMEOUT_SEC))
  while (( SECONDS < deadline )); do
    local s
    s="$(rds_status 2>/dev/null || echo unknown)"
    if [[ "$s" == "$want" ]]; then
      echo "RDS $RDS_INSTANCE_ID status=$s"
      return 0
    fi
    echo "Waiting for RDS $RDS_INSTANCE_ID (now=$s, want=$want)..."
    sleep 15
  done
  echo "Timed out waiting for RDS status $want" >&2
  return 1
}

wait_ec2_state() {
  local want="$1"
  local deadline=$((SECONDS + WAIT_TIMEOUT_SEC))
  while (( SECONDS < deadline )); do
    local s
    s="$(ec2_state 2>/dev/null || echo unknown)"
    if [[ "$s" == "$want" ]]; then
      echo "EC2 $EC2_INSTANCE_ID state=$s"
      return 0
    fi
    echo "Waiting for EC2 $EC2_INSTANCE_ID (now=$s, want=$want)..."
    sleep 10
  done
  echo "Timed out waiting for EC2 state $want" >&2
  return 1
}
