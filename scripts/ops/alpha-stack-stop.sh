#!/usr/bin/env bash
# Alpha Method A — stop app EC2 and staging RDS (no terraform destroy).
# ALB keeps running (still billed). EC2 stop also stops host RabbitMQ on that instance. .env.deploy on the same EBS is preserved.
#
# Usage (from repo root, AWS credentials configured):
#   ./scripts/ops/alpha-stack-stop.sh
#   AWS_REGION=ap-northeast-2 ./scripts/ops/alpha-stack-stop.sh
#
# Optional env: EC2_INSTANCE_ID, RDS_INSTANCE_ID, ALB_TARGET_GROUP_ARN, ASG_NAME, TF_DIR

set -euo pipefail

# shellcheck source=alpha-stack-common.sh
source "$(dirname "$0")/alpha-stack-common.sh"

load_alpha_context

echo "Alpha stack stop (Method A)"
echo "  Region:  $AWS_REGION"
echo "  EC2:     $EC2_INSTANCE_ID"
echo "  RDS:     $RDS_INSTANCE_ID"
echo "  Note:    ALB is not stopped; RabbitMQ on EC2 stops with the instance (see docs/aws-github-oidc-ecr-ssm.md)."

ec2_s="$(ec2_state 2>/dev/null || echo unknown)"
if [[ "$ec2_s" == "running" ]]; then
  echo "Stopping EC2 $EC2_INSTANCE_ID..."
  aws ec2 stop-instances --region "$AWS_REGION" --instance-ids "$EC2_INSTANCE_ID" >/dev/null
  wait_ec2_state "stopped"
elif [[ "$ec2_s" == "stopped" ]]; then
  echo "EC2 already stopped."
else
  echo "EC2 state=$ec2_s (skip stop)"
fi

rds_s="$(rds_status 2>/dev/null || echo unknown)"
if [[ "$rds_s" == "available" ]]; then
  echo "Stopping RDS $RDS_INSTANCE_ID..."
  aws rds stop-db-instance --region "$AWS_REGION" --db-instance-identifier "$RDS_INSTANCE_ID" >/dev/null
  wait_rds_status "stopped"
elif [[ "$rds_s" == "stopped" ]]; then
  echo "RDS already stopped."
else
  echo "RDS status=$rds_s (skip stop; wait until available/stopped if in transition)"
fi

echo "Done. Start with: ./scripts/ops/alpha-stack-start.sh"
