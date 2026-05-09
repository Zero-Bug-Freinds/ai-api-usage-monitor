# AWS: GitHub OIDC for ECR push and SSM/ELB deploy

This document is the operational companion to [`docker-compose.prod.yml`](../../docker-compose.prod.yml), [`.github/workflows/release.yml`](../../.github/workflows/release.yml), and [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml).

## Terraform (IaC)

AWS resources described here can be created as code under **[`infra/terraform/README.md`](../infra/terraform/README.md)** instead of pasting JSON policies manually.

- **Bootstrap** remote state (S3 + DynamoDB lock): **[`infra/terraform/bootstrap/README.md`](../infra/terraform/bootstrap/README.md)** (local state for the bucket/table only).
- **Apply order**: bootstrap backend → root `infra/terraform` (`terraform init` with backend config → `terraform apply`) → copy outputs into GitHub Environment variables (mapping table in the Terraform README).
- **Trust policy**: Terraform roles use GitHub’s environment subject `repo:<org>/<repo>:environment:staging` or `:environment:production`, matching the `staging` / `production` GitHub Environments used by Release and Deploy workflows.
- **Optional stack**: set `enable_compute_stack` for VPC + ALB + target group + ASG + EC2 instance profile; align `TARGET_PORT` with [`scripts/deploy/gha-roll-instance.sh`](../scripts/deploy/gha-roll-instance.sh) (default **80**).

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

## 2. Release role — ECR push only (`AWS_RELEASE_ROLE_ARN`)

GitHub Environment variables: `AWS_RELEASE_ROLE_ARN`, `AWS_REGION`, optional `ECR_REPOSITORY_PREFIX` (default `ai-api-usage-monitor`).

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

## 3. Deploy role — SSM + ELB + read-only ECR (`AWS_DEPLOY_ROLE_ARN`)

Used by [`deploy.yml`](../../.github/workflows/deploy.yml) and [`scripts/deploy/gha-roll-instance.sh`](../../scripts/deploy/gha-roll-instance.sh). EC2 instances use a **separate** instance profile to **pull** images (not this OIDC role).

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

Tags: immutable `${{ github.sha }}` plus moving `:staging` / `:prod` (see `release.yml`).

---

## 6. Secrets on EC2 (SSM Parameter Store / Secrets Manager)

- **Do not** commit production `.env.deploy`. On the host, create `/opt/ai-api-usage-monitor/.env.deploy` (path configurable) from Parameter Store at boot or via SSM `GetParameters` in a thin wrapper before `docker compose`.
- **Pattern**: `/ai-api/staging/IDENTITY_POSTGRES_PASSWORD` (hierarchy by environment); export into `.env.deploy` or use `docker compose --env-file` with a generated file (mode `0600`, root-owned).
- **Rotation**: prefer Secrets Manager rotation + task to refresh env file and `docker compose up -d` for affected services.

---

## 7. ALB Target Group health (canonical)

| Setting | Value |
|---------|--------|
| Protocol / port | HTTP **8080** (dedicated `web-edge` listener; no Host allowlist) |
| Path | `/healthz` |
| Matcher | `200` |
| Healthy threshold | 2 (tune per AZ) |
| Unhealthy threshold | 2 |
| Interval | 15s (tune with deploy duration) |

Traffic listener remains **80** → `web-edge:80` (path routing + Host allowlist for browser traffic).

---

## 8. Previous successful `git sha` (rollback)

- **On instance**: `scripts/deploy/on-instance-compose-roll.sh` writes `/var/lib/ai-api-usage-monitor-deploy/last-success-sha` after a successful local health check.
- On deploy failure after `compose up`, the script attempts `docker compose pull && up -d` with `IMAGE_TAG` reset to that file’s value (rollback).
- **Optional**: mirror the same sha to SSM Parameter Store (`/ai-api/staging/last-success-sha`) from the instance or from GitHub Actions after a green deploy for cross-instance visibility.

---

## 9. GitHub configuration checklist

1. Environments **`staging`** and **`production`** (add required reviewers on `production`).
2. Environment variables: `AWS_REGION`, `AWS_RELEASE_ROLE_ARN`, `ECR_REPOSITORY_PREFIX` (optional), `AWS_DEPLOY_ROLE_ARN`, `ALB_TARGET_GROUP_ARN`, `SSM_DEPLOY_ROOT`, optional `TARGET_PORT`, Next public origins for `release` (`NEXT_PUBLIC_*` as needed). Use **[`infra/terraform/README.md`](../infra/terraform/README.md)** output names when applying IaC (see “Terraform outputs → GitHub Environment variables”).
3. Branch rules: require CI green before merge to `develop` / `main`; optional rule to require `Release` success after merge.
