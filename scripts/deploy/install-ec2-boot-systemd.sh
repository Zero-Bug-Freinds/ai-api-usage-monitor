#!/usr/bin/env bash
# Install systemd units for EC2 boot (run from Terraform user-data as root).

set -euo pipefail

DEPLOY_ROOT="${1:-/opt/ai-api-usage-monitor}"

cat >/etc/systemd/system/ai-api-usage-monitor-boot-compose.service <<UNIT
[Unit]
Description=Restart full compose stack after reboot when deploy state exists
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
Environment=DEPLOY_ROOT=${DEPLOY_ROOT}
ExecStart=${DEPLOY_ROOT}/scripts/deploy/ec2-boot-compose.sh
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target
UNIT

chmod +x "${DEPLOY_ROOT}/scripts/deploy/ec2-boot-compose.sh" 2>/dev/null || true
chmod +x "${DEPLOY_ROOT}/scripts/deploy/ec2-bootstrap-health-edge.sh" 2>/dev/null || true
systemctl daemon-reload
systemctl enable ai-api-usage-monitor-boot-compose.service
