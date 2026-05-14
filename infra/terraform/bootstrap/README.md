# Terraform remote state bootstrap

Creates an **S3 bucket** (versioned, encrypted, private) and a **DynamoDB table** for Terraform state locking. This folder uses **local state** intentionally so the bucket itself is not stored in the same remote backend it defines.

## Prerequisites

- AWS credentials with permission to create S3 buckets and DynamoDB tables in the chosen region.
- A globally unique `bucket_name` (S3 namespace is global).

## Steps

1. Copy `terraform.tfvars.example` to `terraform.tfvars` (gitignored if placed here; prefer env-specific paths outside the repo).
2. From this directory:

```bash
terraform init
terraform apply
```

3. Note outputs `state_bucket_name` and `state_lock_table_name`.

4. Configure the root module under `infra/terraform/` with a backend file (see `../backend.hcl.example` and `../README.md`).

## Optional KMS

Set `kms_key_arn` to enforce SSE-KMS on the state bucket. Leave empty for SSE-S3 (`AES256`).
