# AWS: GitHub OIDC for ECR push and SSM/ELB deploy

This document is the operational companion to [`docker-compose-prod.yml`](../../docker-compose-prod.yml), [`.github/workflows/release.yml`](../../.github/workflows/release.yml), and [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml).

## Terraform (IaC)

AWS resources described here can be created as code under **[`infra/terraform/README.md`](../infra/terraform/README.md)** instead of pasting JSON policies manually.

- **Optional — remote state bootstrap** (S3 + DynamoDB lock): **[`infra/terraform/bootstrap/README.md`](../infra/terraform/bootstrap/README.md)**. Many teams run the root module with **local `terraform.tfstate`** only; bootstrap is not mandatory.
- **Apply order**: `cd infra/terraform` → `terraform init` (add backend config only if you adopt S3 remote state) → `terraform apply` → align GitHub Environment variables and workflow YAML with Terraform outputs (mapping table in the Terraform README).
- **Trust policy (root module)**: IAM roles use `StringLike` on `repo:<org>/<repo>:*` (see §1.2), so tokens from jobs that use GitHub Environments (`environment: staging` / `production`) still match.
- **Optional stack**: set `enable_compute_stack` for VPC + ALB + target group + ASG + EC2 instance profile; align `TARGET_PORT` with [`scripts/deploy/gha-roll-instance.sh`](../scripts/deploy/gha-roll-instance.sh) (default **8888**, `terraform output alb_target_port`).
- **Terraform from Actions**: [`.github/workflows/terraform-aws.yml`](../.github/workflows/terraform-aws.yml) runs `plan` / `apply` using repository secrets (long-lived IAM user keys), separate from OIDC-based Release/Deploy.

## CD automation (Release + Deploy)

- **[`release.yml`](../.github/workflows/release.yml)** — after a successful **ECR build/push** when path filters (or `force_rebuild_all` on `workflow_dispatch`) imply at least one image was eligible to push, job **`roll-after-ecr`** runs in the same Environment. If **`ALB_TARGET_GROUP_ARN`** is set, it assumes the **deploy IAM role** configured in that workflow’s `env` (`AWS_DEPLOY_ROLE_ARN`, OIDC), lists EC2 instance targets with [`scripts/deploy/list-tg-instance-ids.sh`](../scripts/deploy/list-tg-instance-ids.sh), then runs [`scripts/deploy/gha-roll-instance.sh`](../scripts/deploy/gha-roll-instance.sh) per instance with **`IMAGE_TAG` = commit SHA**. If `ALB_TARGET_GROUP_ARN` is unset or the target group has no `i-*` targets, the roll step is skipped (ECR-only is still success).
- **[`deploy.yml`](../.github/workflows/deploy.yml)** — **`workflow_dispatch`** for on-demand rolls: **`image_tag`** is required; **instance IDs** may be left empty to use the same target-group discovery. Set optional Environment variable **`TARGET_PORT`** so the workflow exports it (default **8888** in the shell script).
- **Concurrency**: `release.yml` and `deploy.yml` use the same **`alb-ssm-roll-<staging|production>`** concurrency group so automatic and manual rolls for an environment do not overlap.

If the GitHub OIDC provider already exists in the account, import it before the first apply (command in the Terraform README).

## 1. GitHub OIDC trust (both roles)

Create **two** IAM roles (recommended minimum split) or one combined role for small teams.

### 1.1 OIDC provider (once per account)

If not already present: IAM → Identity providers → Add **OpenID Connect** — URL `https://token.actions.githubusercontent.com`, audience `sts.amazonaws.com`.

### 1.2 Trust policy (attach to each role)

Replace `ACCOUNT_ID`, `ORG_OR_USER`, and `REPO` (`ai-api-usage-monitor`). Tighten `sub` to a single repository.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "GitHubActionsOidc",
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
        },
        "StringLike": {
          "token.actions.githubusercontent.com:sub": "repo:ORG_OR_USER/REPO:*"
        }
      }
    }
  ]
}
```

Optional: restrict to environment branches only, e.g. `repo:ORG/REPO:ref:refs/heads/develop` and `ref:refs/heads/main` via separate roles.

---

## 2. Release role — ECR push only (`ReleaseRole` / `AWS_RELEASE_ROLE_ARN`)

**Workflows:** [`.github/workflows/release.yml`](../.github/workflows/release.yml) sets the OIDC **`role-to-assume`** in workflow `env` as `AWS_RELEASE_ROLE_ARN` (must match the IAM role Terraform or your console created). **GitHub Environment** still needs **`AWS_REGION`**, optional **`ECR_REPOSITORY_PREFIX`** (default `ai-api-usage-monitor`), and other vars from §9 — you do **not** need to duplicate the release role ARN on the Environment unless you change the workflow to read `vars.AWS_RELEASE_ROLE_ARN` again.

### 2.1 Permission policy (minimal shape)

Replace `ACCOUNT_ID` and `REGION`. Scope `Resource` to your ECR repositories if you create them upfront; otherwise the `arn:aws:ecr:...:repository/ai-api-usage-monitor/*` pattern matches one prefix.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EcrAuthToken",
      "Effect": "Allow",
      "Action": ["ecr:GetAuthorizationToken"],
      "Resource": "*"
    },
    {
      "Sid": "EcrPushScoped",
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:CompleteLayerUpload",
        "ecr:InitiateLayerUpload",
        "ecr:PutImage",
        "ecr:UploadLayerPart",
        "ecr:BatchGetImage",
        "ecr:GetDownloadUrlForLayer"
      ],
      "Resource": "arn:aws:ecr:REGION:ACCOUNT_ID:repository/ai-api-usage-monitor/*"
    }
  ]
}
```

---

## 3. Deploy role — SSM + ELB (`DeployRole` / `AWS_DEPLOY_ROLE_ARN`)

Used by [`deploy.yml`](../../.github/workflows/deploy.yml) and [`release.yml`](../../.github/workflows/release.yml) job **`roll-after-ecr`** (OIDC `role-to-assume` from workflow `env`). [`scripts/deploy/gha-roll-instance.sh`](../../scripts/deploy/gha-roll-instance.sh) runs under that role in Actions. EC2 instances use a **separate** instance profile to **pull** images (not this OIDC role).

### 3.1 Permission policy (minimal shape)

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ElbRegisterDeregister",
      "Effect": "Allow",
      "Action": [
        "elasticloadbalancing:RegisterTargets",
        "elasticloadbalancing:DeregisterTargets",
        "elasticloadbalancing:DescribeTargetHealth"
      ],
      "Resource": "*"
    },
    {
      "Sid": "SsmSendCommand",
      "Effect": "Allow",
      "Action": [
        "ssm:SendCommand",
        "ssm:GetCommandInvocation",
        "ssm:ListCommandInvocations"
      ],
      "Resource": "*"
    }
  ]
}
```

Tighten `Resource` to specific target groups and SSM documents when your IaC names are stable (recommended for production).

---

## 4. EC2 instance profile (pull only, on the host)

Attach to instances in the ASG:

- `ecr:GetAuthorizationToken` (Resource `*`)
- `ecr:BatchGetImage`, `ecr:GetDownloadUrlForLayer` on `arn:aws:ecr:REGION:ACCOUNT_ID:repository/ai-api-usage-monitor/*`
- `ssm:UpdateInstanceInformation` and AmazonSSMManagedInstanceCore (AWS managed policy) so SSM Agent can receive commands

Do **not** attach the GitHub OIDC role to EC2.

---

## 5. ECR repository naming

One repository per image component (flat under prefix `ai-api-usage-monitor/`):

| ECR repository name | Purpose |
|---------------------|---------|
| `ai-api-usage-monitor/api-gateway-service` | API Gateway |
| `ai-api-usage-monitor/proxy-service` | Proxy |
| `ai-api-usage-monitor/identity-service` | Identity backend |
| `ai-api-usage-monitor/usage-service` | Usage backend |
| `ai-api-usage-monitor/billing-service` | Billing backend |
| `ai-api-usage-monitor/team-service` | Team backend |
| `ai-api-usage-monitor/notification-service` | Notification backend |
| `ai-api-usage-monitor/agent-service` | Agent backend |
| `ai-api-usage-monitor/identity-web` | Identity Next standalone |
| `ai-api-usage-monitor/usage-web` | Usage Next standalone |
| `ai-api-usage-monitor/billing-web` | Billing Next standalone |
| `ai-api-usage-monitor/team-web` | Team Next standalone |
| `ai-api-usage-monitor/notification-web` | Notification Next standalone |
| `ai-api-usage-monitor/agent-web` | Agent Next standalone |
| `ai-api-usage-monitor/web-edge` | Nginx edge (`docker/web-edge/Dockerfile`) |

Tags: immutable `${{ github.sha }}` plus moving `:staging` / `:prod` (see `release.yml`). When using **[`infra/terraform`](../infra/terraform/README.md)**, ECR repositories get an **untagged image lifecycle** (default expire after 14 days; see variable `ecr_untagged_image_expire_days`). The Terraform variable **`ecr_repository_suffixes`** controls which repositories exist in AWS (defaults in **`infra/terraform/variables.tf`** match **`release.yml`** image suffixes; override in `terraform.tfvars` if needed).

---

## 6. Alpha cost saving — stop/start without destroy (Method A)

When `enable_compute_stack` and `enable_staging_rds` are on, use the repo scripts (AWS CLI + `terraform output`):

| Script | Action |
|--------|--------|
| [`scripts/ops/alpha-stack-status.sh`](../scripts/ops/alpha-stack-status.sh) | EC2 / RDS / ALB DNS state |
| [`scripts/ops/alpha-stack-stop.sh`](../scripts/ops/alpha-stack-stop.sh) | Stop EC2, then stop RDS (passwords and `.env.deploy` on the same volume are kept) |
| [`scripts/ops/alpha-stack-start.sh`](../scripts/ops/alpha-stack-start.sh) | Start RDS, wait until `available`, start EC2 (systemd may run `ec2-boot-compose.sh`) |

**Still billed while “stopped”:** ALB, EBS, RDS storage.

**Do not** use `terraform destroy` for nightly shutdown if you want stable RDS passwords and data; use these scripts instead. RabbitMQ (if installed on EC2) stops with the instance.

---

## 7. Secrets on EC2 (SSM Parameter Store / Secrets Manager)

- **Do not** commit production `.env.deploy`. On the host, create `/opt/ai-api-usage-monitor/.env.deploy` (path configurable) from Parameter Store at boot or via SSM `GetParameters` in a thin wrapper before `docker compose`.
- **Pattern**: `/ai-api/staging/IDENTITY_POSTGRES_PASSWORD` (hierarchy by environment); export into `.env.deploy` or use `docker compose --env-file` with a generated file (mode `0600`, root-owned).
- **Rotation**: prefer Secrets Manager rotation + task to refresh env file and `docker compose up -d` for affected services.

---

## 8. ALB Target Group health (canonical)

Defaults in [`infra/terraform`](../infra/terraform) align **traffic** and **registration** with **web-edge** on the host:

| Setting | Default value |
|---------|----------------|
| Target group **port** (instance registration) | **8888** (`alb_target_port`) — maps to `WEB_EDGE_HOST_PORT` / `docker-compose-prod.yml` |
| Health check **port** | **`traffic-port`** — same as target port |
| Path | `/healthz` |
| Matcher | `200` |
| Healthy threshold | 2 (tune per AZ) |
| Unhealthy threshold | 2 |
| Interval | 15s (tune with deploy duration) |

`web-edge` serves `/healthz` on the main **in-container :80** listener (including a `server_name` regex for RFC1918 hosts so ALB IP-style health checks succeed). Alternatively set **`alb_health_check_port` = `"8080"`** in Terraform to use the dedicated in-container health listener only; the compute module then opens the instance security group from the ALB to **8080** automatically when it differs from `alb_target_port`.

The ALB listener stays **HTTP :80** on the load balancer; targets are **instance:8888** (path routing + Host allowlist for browser traffic on `web-edge`).

---

## 9. Previous successful `git sha` (rollback)

- **On instance**: `scripts/deploy/on-instance-compose-roll.sh` writes `/var/lib/ai-api-usage-monitor-deploy/last-success-sha` after a successful local health check.
- On deploy failure after `compose up`, the script attempts `docker compose pull && up -d` with `IMAGE_TAG` reset to that file’s value (rollback).
- **Optional**: mirror the same sha to SSM Parameter Store (`/ai-api/staging/last-success-sha`) from the instance or from GitHub Actions after a green deploy for cross-instance visibility.

---

## 10. GitHub configuration checklist

1. Environments **`staging`** and **`production`** (add required reviewers on `production`).
2. **Per Environment variables:** `AWS_REGION`, optional `ECR_REPOSITORY_PREFIX`, `ALB_TARGET_GROUP_ARN` (set for post-Release auto roll), `SSM_DEPLOY_ROOT` (use `terraform output ssm_deploy_root_default`), optional `TARGET_PORT` (use `terraform output alb_target_port`, or leave unset — workflows default to **8888**), Next public origins for `release` (`NEXT_PUBLIC_*` as needed). **Release / Deploy OIDC role ARNs** are pinned in [`.github/workflows/release.yml`](../.github/workflows/release.yml) and [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) `env`; after `terraform apply`, ensure those ARNs match **`terraform output aws_release_role_arn`** / **`aws_deploy_role_arn`**, or switch the workflows back to `vars.AWS_*_ROLE_ARN` if you prefer Environment-stored ARNs.
3. **Terraform from GitHub Actions (optional):** repository secrets `AWS_SECRET_ACCESS_KEY` and `AWS_ACCESS_KEY_ID` (or `AWS_ACCESS_KEY`) for [`.github/workflows/terraform-aws.yml`](../.github/workflows/terraform-aws.yml); optional repo variable `AWS_REGION`.
4. Branch rules: require CI green before merge to `develop` / `main`; optional rule to require `Release` success after merge.
5. **Messaging (RabbitMQ):** this stack does **not** use **Amazon MQ**. With **`enable_ec2_rabbitmq`** (Terraform default when compute is on), the broker runs in **Docker on the EC2 host**; app containers use **`RABBITMQ_HOST=host.docker.internal`** in [`.env.deploy.example`](../.env.deploy.example) / deploy roll merge from `terraform-rabbitmq.env`. Do not point `RABBITMQ_*` at an `*.mq.amazonaws.com` endpoint unless you deliberately change the architecture.

Details and output mapping: **[`infra/terraform/README.md`](../infra/terraform/README.md)**.
