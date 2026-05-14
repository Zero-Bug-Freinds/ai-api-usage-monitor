# Terraform (AWS IaC for CD prerequisites)

This root manages **GitHub OIDC** (`token.actions.githubusercontent.com`), **two IAM roles** used by GitHub Actions (**`ReleaseRole`** — ECR push, **`DeployRole`** — ELB + SSM), **ECR repositories** (suffix list from `ecr_repository_suffixes`), and an **optional** VPC + ALB + ASG stack with an EC2 instance profile (ECR pull + SSM).

Application CD remains **GitHub Actions + SSM + Compose**; Terraform replaces **manual IAM/ECR JSON** from [`docs/aws-github-oidc-ecr-ssm.md`](../../docs/aws-github-oidc-ecr-ssm.md).

The reusable module [`modules/github_env_roles`](modules/github_env_roles) (per–GitHub-Environment roles) is **not** wired from the root `main.tf`; the root module defines the single release/deploy role pair above. Teams may fork that pattern if they need separate roles per environment.

## Versions

- Terraform `>= 1.9`
- AWS provider `~> 5.100` (see [`versions.tf`](versions.tf))
- Random provider `~> 3.6` (RDS master password)

## Apply order

1. **Optional — remote state bootstrap** (teams that want S3 + DynamoDB locking): [`bootstrap/`](bootstrap/README.md). Skip if you use **local Terraform state** only.
2. **Root module** (`infra/terraform/`): `cd infra/terraform` → `terraform init` (add `-backend-config=backend.hcl` only when using the S3 backend) → `terraform apply`.
3. **Optional compute**: set `enable_compute_stack = true` when you want Terraform-managed VPC/ALB/TG/ASG (see variables). You can start with `false`, apply IAM+ECR, then enable compute in a follow-up apply.
4. **Optional staging RDS**: set `enable_staging_rds = true` (requires compute) to provision one small PostgreSQL instance in the compute VPC for staging logical DBs — **not** production per-service physical isolation; see [`docs/msa-database-and-service-integration.md`](../../docs/msa-database-and-service-integration.md). After apply, use outputs `staging_rds_*` and [`scripts/deploy/rds-staging-create-logical-dbs.sh`](../../scripts/deploy/rds-staging-create-logical-dbs.sh) from EC2.
5. **GitHub**: configure **Environment variables** on `staging` and `production` (see table below). **[`.github/workflows/release.yml`](../../.github/workflows/release.yml)** and **[`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml)** pin **OIDC role ARNs** in workflow `env`; change those YAML blocks if role names or account IDs change. For **Terraform CLI from Actions**, use **[`.github/workflows/terraform-aws.yml`](../../.github/workflows/terraform-aws.yml)** (repository secrets: `AWS_SECRET_ACCESS_KEY` and `AWS_ACCESS_KEY_ID` or `AWS_ACCESS_KEY`; optional repo variable `AWS_REGION`).

### Workspaces vs single state

This repository uses **one state file** per root apply. The root creates **one** `ReleaseRole` and **one** `DeployRole` (same pair for both GitHub Environments). OIDC trust uses `StringLike` on `repo:<github_org>/<github_repo>:*`, which still allows jobs that use `environment: staging` / `production` (their `sub` claim matches the pattern).

If you prefer isolated states (e.g. separate AWS accounts), use a second root or change `key` in the S3 backend per stack (when using remote state).

## Backend configuration

**Local state (default in this repo when you skip backend config):** run `terraform init` with no backend file; state stays in `terraform.tfstate` (do not commit it).

**Optional S3 backend:** do not commit secrets. If the root module includes a `backend "s3" {}` block (HashiCorp partial configuration), copy [`backend.hcl.example`](backend.hcl.example) to `backend.hcl` (gitignored), then:

```bash
cd infra/terraform
terraform init -backend-config=backend.hcl
```

Use distinct `key` values if you split environments into separate state files.

## Variables

Copy [`terraform.tfvars.example`](terraform.tfvars.example) and adjust as needed.

- **`github_org`**, **`github_repo`** — must match the GitHub repo for OIDC `sub` (defaults in `variables.tf` are placeholders for CI; set real values for your org).
- **`ecr_repository_prefix`** — defaults to `ai-api-usage-monitor` (matches `release.yml`).
- **`ecr_repository_suffixes`** — list of repository path segments after the prefix (defaults match **[`.github/workflows/release.yml`](../../.github/workflows/release.yml)** image names). Align `terraform.tfvars` if you override.
- **`release_iam_role_name`**, **`deploy_iam_role_name`** — IAM role names (defaults `ReleaseRole`, `DeployRole`).
- **`ecr_untagged_image_expire_days`** — ECR lifecycle for untagged images (default **14**).
- **`enable_compute_stack`**, **`compute_environment_label`**, sizing, **`alb_target_port`**, **`alb_health_check_path`**, **`vpc_cidr`**, **`public_subnet_cidrs`** — optional compute stack.
- **`enable_staging_rds`**, **`staging_rds_instance_class`**, **`staging_rds_allocated_storage`** — optional single Postgres RDS in the compute VPC (staging only).

## OIDC trust shape

IAM roles use **AssumeRoleWithWebIdentity** from the GitHub OIDC provider, audience `sts.amazonaws.com`, and **`StringLike`** on:

`repo:<github_org>/<github_repo>:*`

This matches the manual JSON in [`docs/aws-github-oidc-ecr-ssm.md`](../../docs/aws-github-oidc-ecr-ssm.md) §1.2. It is broader than `repo:…:environment:staging` alone but includes those subjects.

If an OIDC provider for `token.actions.githubusercontent.com` already exists in the account, import it before first apply:

```bash
terraform import aws_iam_openid_connect_provider.github arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com
```

## Terraform outputs → GitHub (and workflows)

**Role ARNs:** [`.github/workflows/release.yml`](../../.github/workflows/release.yml) and [`.github/workflows/deploy.yml`](../../.github/workflows/deploy.yml) currently set `AWS_RELEASE_ROLE_ARN` / `AWS_DEPLOY_ROLE_ARN` in workflow **`env`**. After `terraform apply`, confirm those ARNs match **`terraform output aws_release_role_arn`** and **`terraform output aws_deploy_role_arn`**, or update the YAML to the output values.

Map the rest into **each** GitHub Environment (`staging`, `production`) as **Variables** (not secrets unless your policy requires):

| GitHub Environment variable | Source |
|------------------------------|--------|
| `AWS_REGION` | Same as `var.aws_region` (or set manually; optional repo **Variable** `AWS_REGION` is used by `terraform-aws.yml`). |
| _(account ID for imports)_ | Output `aws_account_id` (OIDC provider import path). |
| `ECR_REPOSITORY_PREFIX` | Same as `var.ecr_repository_prefix` (default `ai-api-usage-monitor`). |
| `ALB_TARGET_GROUP_ARN` | Output `alb_target_group_arn` when `enable_compute_stack = true`; otherwise set manually from your ALB stack (needed for **post-Release auto roll** and optional TG discovery in [`deploy.yml`](../../.github/workflows/deploy.yml)). |
| `TARGET_PORT` | Output `alb_target_port` when compute is on, else `var.alb_target_port` — must match [`scripts/deploy/gha-roll-instance.sh`](../../scripts/deploy/gha-roll-instance.sh) (default **80**). Set on the Environment so [`deploy.yml`](../../.github/workflows/deploy.yml) and [`release.yml`](../../.github/workflows/release.yml) `roll-after-ecr` pass it through. |

Also configure workflow-specific vars documented in [`docs/aws-github-oidc-ecr-ssm.md`](../../docs/aws-github-oidc-ecr-ssm.md) (`SSM_DEPLOY_ROOT`, Next public origins for `release`, etc.).

When **`enable_staging_rds`** is on, copy **`terraform output staging_rds_address`** (and the sensitive master password) into the deploy host `.env.deploy` for Postgres hosts/passwords (staging shortcut: use user **`appadmin`** for every `*_POSTGRES_USER` after running [`scripts/deploy/rds-staging-create-logical-dbs.sh`](../../scripts/deploy/rds-staging-create-logical-dbs.sh) on EC2). **RabbitMQ is not created by this module** — still use Amazon MQ or your broker and set `RABBITMQ_*` / `NOTIFICATION_RABBITMQ_URL` manually.

### Deploy IAM policy shape

The root module’s **DeployRole** inline policy follows [`docs/aws-github-oidc-ecr-ssm.md`](../../docs/aws-github-oidc-ecr-ssm.md) §3.1 (`Resource: "*"` for ELB register/deregister/describe health and SSM command APIs). Tighten in Terraform when target group and SSM document ARNs are stable.

The GitHub OIDC provider includes **two thumbprints** for `token.actions.githubusercontent.com` (rotation-friendly); see `main.tf`.

## Optional compute stack

Module [`modules/compute_stack`](modules/compute_stack): simplified **public subnet** layout (ALB + ASG), **HTTP :80** listener → target group using `alb_target_port` (default **80**) so defaults match `gha-roll-instance.sh`. User data installs **amazon-ssm-agent** (enables SSM Run Command), Docker and Compose plugin on Amazon Linux 2023, and creates `/opt/<project_name>/` as a plausible clone/deploy parent (align `SSM_DEPLOY_ROOT` with your layout). **Existing** EC2 do not re-run user data: after changing bootstrap, start an ASG **instance refresh** (or replace instances) so new nodes pick up the agent.

For production hardening, replace public subnets with private app subnets + NAT (not included here).

## CI validation

Repository CI runs `terraform fmt -check` and `terraform validate` when `infra/terraform/**` changes (see [`docs/CI.md`](../../docs/CI.md)).
