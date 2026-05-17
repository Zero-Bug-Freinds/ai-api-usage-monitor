#!/usr/bin/env bash
# Alpha Method A — start staging RDS then EC2 (same instances; passwords unchanged).
# After EC2 boots, systemd runs ec2-boot-compose when .env.deploy + last-success-sha exist.
#
# Usage:
#   ./scripts/ops/alpha-stack-start.sh
#   ./scripts/ops/alpha-stack-start.sh --wait-alb   # optional: poll ALB /healthz via DNS
#
# Optional env: same as alpha-stack-stop.sh; WAIT_TIMEOUT_SEC (default 1800)

set -euo pipefail

WAIT_ALB=false
for arg in "$@"; do
  case "$arg" in
    --wait-alb) WAIT_ALB=true ;;
    -h | --help)
      echo "Usage: $0 [--wait-alb]"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

# shellcheck source=alpha-stack-common.sh
source "$(dirname "$0")/alpha-stack-common.sh"

load_alpha_context

echo "Alpha stack start (Method A)"
echo "  Region:  $AWS_REGION"
echo "  RDS:     $RDS_INSTANCE_ID"
echo "  EC2:     $EC2_INSTANCE_ID"

rds_s="$(rds_status 2>/dev/null || echo unknown)"
if [[ "$rds_s" == "stopped" ]]; then
  echo "Starting RDS $RDS_INSTANCE_ID..."
  aws rds start-db-instance --region "$AWS_REGION" --db-instance-identifier "$RDS_INSTANCE_ID" >/dev/null
  wait_rds_status "available"
elif [[ "$rds_s" == "available" ]]; then
  echo "RDS already available."
else
  echo "RDS status=$rds_s — waiting for available..."
  wait_rds_status "available"
fi

ec2_s="$(ec2_state 2>/dev/null || echo unknown)"
if [[ "$ec2_s" == "stopped" ]]; then
  echo "Starting EC2 $EC2_INSTANCE_ID..."
  aws ec2 start-instances --region "$AWS_REGION" --instance-ids "$EC2_INSTANCE_ID" >/dev/null
  wait_ec2_state "running"
elif [[ "$ec2_s" == "running" ]]; then
  echo "EC2 already running."
else
  wait_ec2_state "running"
fi

echo "EC2 is up; compose should restart via systemd (ai-api-usage-monitor-boot-compose) when deploy state exists."
echo "If the app is down, SSM: sudo bash scripts/deploy/on-instance-compose-roll.sh with IMAGE_TAG set, or run Release roll."

if [[ "$WAIT_ALB" == true ]]; then
  alb_dns="$(tf_raw alb_dns_name)"
  if [[ -z "$alb_dns" ]]; then
    echo "No alb_dns_name terraform output; skip ALB wait." >&2
    exit 0
  fi
  url="http://${alb_dns}/healthz"
  deadline=$((SECONDS + 600))
  echo "Waiting for $url ..."
  while (( SECONDS < deadline )); do
    if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then
      echo "ALB health OK: $url"
      exit 0
    fi
    sleep 10
  done
  echo "ALB health not ready within 600s (compose may still be starting)." >&2
  exit 1
fi

echo "Done."
