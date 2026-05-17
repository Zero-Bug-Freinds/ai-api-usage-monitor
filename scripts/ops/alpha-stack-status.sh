#!/usr/bin/env bash
# Print alpha stack state (EC2, RDS, ALB DNS) for Method A stop/start workflows.

set -euo pipefail

# shellcheck source=alpha-stack-common.sh
source "$(dirname "$0")/alpha-stack-common.sh"

load_alpha_context 2>/dev/null || {
  echo "Failed to load stack context (terraform outputs or env vars)." >&2
  exit 1
}

alb_dns="$(tf_raw alb_dns_name)"
mq_enabled="$(tf_raw staging_mq_enabled)"

echo "Alpha stack status (region=$AWS_REGION)"
echo "  EC2:  $EC2_INSTANCE_ID  state=$(ec2_state 2>/dev/null || echo unknown)"
echo "  RDS:  $RDS_INSTANCE_ID  status=$(rds_status 2>/dev/null || echo unknown)"
echo "  ALB:  ${alb_dns:-<unset>}"
echo "  MQ:   enabled=${mq_enabled:-unknown} (Amazon MQ is not stopped by alpha-stack-stop; still billed when running)"
echo ""
echo "Stop:  ./scripts/ops/alpha-stack-stop.sh"
echo "Start: ./scripts/ops/alpha-stack-start.sh"
