# Terraform (AWS IaC for CD prerequisites)

This root manages **GitHub OIDC**, **per-environment IAM roles** for Release/Deploy workflows, **ECR repositories** aligned with [`.github/workflows/release.yml`](../../.github/workflows/release.yml), and an **optional** VPC + ALB + ASG stack with an EC2 instance profile (ECR pull + SSM).

Application CD remains **GitHub Actions + SSM + Compose**; Terraform replaces **manual IAM/ECR JSON** from [`docs/aws-github-oidc-ecr-ssm.md`](../../docs/aws-github-oidc-ecr-ssm.md).

## Versions

- Terraform `>= 1.9`
- AWS provider `~> 5.100` (see [`versions.tf`](versions.tf))

## Apply order

1. **Optional — remote state bootstrap** (teams that want S3 + DynamoDB locking): [`bootstrap/`](bootstrap/README.md). Skip if you use **local Terraform state** only.
2. **Root module** (`infra/terraform/`): `cd infra/terraform` → `terraform init` (add `-backend-config=backend.hcl` only when using the S3 backend) → `terraform apply`.
3. **Optional compute**: set `enable_compute_stack = true` when you want Terraform-managed VPC/ALB/TG/ASG (see variables). You can start with `false`, apply IAM+ECR, then enable compute in a follow-up apply.
4. **GitHub**: copy Terraform outputs into **GitHub Environment variables** for `staging` and `production` (table below).

### Workspaces vs single state

This repository uses **one state file** and creates **both** `staging` and `production` IAM roles in the same apply (distinct GitHub Environment OIDC subjects). If you prefer isolated states (e.g. separate AWS accounts), use a second root or change `key` in the S3 backend per stack (when using remote state).

## Backend configuration

**Local state (default in this repo when you skip backend config):** run `terraform init` with no backend file; state stays in `terraform.tfstate` (do not commit it).

**Optional S3 backend:** do not commit secrets. If the root module includes a `backend "s3" {}` block (HashiCorp partial configuration), copy [`backend.hcl.example`](backend.hcl.example) to `backend.hcl` (gitignored), then:

```bash
cd infra/terraform
terraform init -backend-config=backend.hcl
```

Use distinct `key` values if you split environments into separate state files.

## Variables

Copy [`terraform.tfvars.example`](terraform.tfvars.example) and set at least:

- `aws_region`
- `github_org`, `github_repo`

Optional:

- `ecr_repository_prefix` (defaults to `ai-api-usage-monitor`, matching `release.yml`)
- `ecr_untagged_image_expire_days` (default **14**) — ECR lifecycle policy for untagged images
- `extra_deploy_elb_target_group_arns` — when `enable_compute_stack` is false, merge these TG ARNs into deploy-role ELB register/deregister scoping (otherwise `Resource "*"`)
- `enable_compute_stack`, `compute_environment_label`, sizing variables

## OIDC trust shape

IAM roles trust GitHub tokens whose subject is:

`repo:<github_org>/<github_repo>:environment:<staging|production>`

This matches [`release.yml`](../../.github/workflows/release.yml) and [`deploy.yml`](../../.github/workflows/deploy.yml), which select the GitHub Environment `staging` or `production`.

If an OIDC provider for `token.actions.githubusercontent.com` already exists in the account, import it before first apply:

```bash
terraform import aws_iam_openid_connect_provider.github arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com
```

## Terraform outputs → GitHub Environment variables

Map outputs into **each** GitHub Environment (`staging`, `production`) as **Variables** (not secrets unless your policy requires):

| GitHub Environment variable | Terraform output / source |
|------------------------------|---------------------------|
| `AWS_REGION` | Same as `var.aws_region` (no output; set manually). |
| _(account ID for imports)_ | Output `aws_account_id` (OIDC provider import path). |
| `ECR_REPOSITORY_PREFIX` | Same as `var.ecr_repository_prefix` (default `ai-api-usage-monitor`). |
| `AWS_RELEASE_ROLE_ARN` | `aws_release_role_arn_staging` or `aws_release_role_arn_production`. |
| `AWS_DEPLOY_ROLE_ARN` | `aws_deploy_role_arn_staging` or `aws_deploy_role_arn_production`. |
| `ALB_TARGET_GROUP_ARN` | `alb_target_group_arn` when `enable_compute_stack = true`; otherwise set manually from your ALB stack (needed for **post-Release auto roll** and optional TG discovery in [`deploy.yml`](../../.github/workflows/deploy.yml)). |
| `TARGET_PORT` | `alb_target_port` — must match [`scripts/deploy/gha-roll-instance.sh`](../../scripts/deploy/gha-roll-instance.sh) (`TARGET_PORT`, default **80**). Set as an Environment variable so [`deploy.yml`](../../.github/workflows/deploy.yml) and [`release.yml`](../../.github/workflows/release.yml) `roll-after-ecr` pass it through. |

Also configure workflow-specific vars documented in [`docs/aws-github-oidc-ecr-ssm.md`](../../docs/aws-github-oidc-ecr-ssm.md) (`SSM_DEPLOY_ROOT`, Next public origins, etc.).

### Deploy IAM tightening

When `enable_compute_stack` is true, both deploy roles scope `elasticloadbalancing:RegisterTargets` / `DeregisterTargets` to the created target group ARN (plus any `extra_deploy_elb_target_group_arns`). With compute disabled and no extra ARNs, those actions stay on `Resource "*"` (same permissive shape as the manual JSON in the doc).

The GitHub OIDC provider includes **two thumbprints** for `token.actions.githubusercontent.com` (rotation-friendly); see `main.tf`.

## Optional compute stack

Module [`modules/compute_stack`](modules/compute_stack): simplified **public subnet** layout (ALB + ASG), **HTTP :80** listener → target group using `alb_target_port` (default **80**) so defaults match `gha-roll-instance.sh`. User data installs Docker and Compose plugin on Amazon Linux 2023 and creates `/opt/<project_name>/` as a plausible clone/deploy parent (align `SSM_DEPLOY_ROOT` with your layout).

For production hardening, replace public subnets with private app subnets + NAT (not included here).

## CI validation

Repository CI runs `terraform fmt -check` and `terraform validate` when `infra/terraform/**` changes (see [`docs/CI.md`](../../docs/CI.md)).
