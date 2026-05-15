output "aws_account_id" {
  description = "AWS account ID for this configuration (e.g. terraform import OIDC ARN path)."
  value       = data.aws_caller_identity.current.account_id
}

output "github_oidc_provider_arn" {
  description = "IAM OIDC provider ARN for GitHub Actions (token.actions.githubusercontent.com)."
  value       = aws_iam_openid_connect_provider.github.arn
}

output "ecr_repository_names" {
  description = "Map of image suffix to full ECR repository name (matches release.yml paths when suffixes align)."
  value       = module.ecr.repository_names
}

output "aws_release_role_arn" {
  description = "ARN of the IAM role used by release.yml for ECR (OIDC); keep in sync with workflow env AWS_RELEASE_ROLE_ARN."
  value       = aws_iam_role.release.arn
}

output "aws_deploy_role_arn" {
  description = "ARN of the IAM role used by release.yml / deploy.yml for ALB+SSM (OIDC); keep in sync with workflow env AWS_DEPLOY_ROLE_ARN."
  value       = aws_iam_role.deploy.arn
}

output "alb_target_group_arn" {
  description = "Present when enable_compute_stack is true; maps to ALB_TARGET_GROUP_ARN in GitHub."
  value       = var.enable_compute_stack ? module.compute[0].alb_target_group_arn : null
}

output "alb_dns_name" {
  description = "Optional compute stack ALB DNS name."
  value       = var.enable_compute_stack ? module.compute[0].alb_dns_name : null
}

output "alb_target_port" {
  description = "Target group / registration port; set GitHub TARGET_PORT to match scripts/deploy/gha-roll-instance.sh."
  value       = var.enable_compute_stack ? module.compute[0].alb_target_port : var.alb_target_port
}

output "alb_health_check_port" {
  description = "ALB target group health check port (traffic-port or fixed port); must match web-edge and instance SG."
  value       = var.enable_compute_stack ? module.compute[0].alb_health_check_port : var.alb_health_check_port
}

output "ec2_instance_profile_name" {
  description = "Optional compute stack instance profile (ECR pull + SSM)."
  value       = var.enable_compute_stack ? module.compute[0].ec2_instance_profile_name : null
}

output "staging_rds_address" {
  description = "Postgres host when staging RDS is enabled; use in .env.deploy *_POSTGRES_HOST."
  value       = length(module.staging_rds) > 0 ? module.staging_rds[0].address : null
}

output "staging_rds_port" {
  value = length(module.staging_rds) > 0 ? module.staging_rds[0].port : null
}

output "staging_rds_master_username" {
  value = length(module.staging_rds) > 0 ? module.staging_rds[0].master_username : null
}

output "staging_rds_master_password" {
  description = "Sensitive. Staging only — copy to SSM or .env.deploy; create logical DBs from EC2 (scripts/deploy/rds-staging-create-logical-dbs.sh)."
  value       = length(module.staging_rds) > 0 ? module.staging_rds[0].master_password : null
  sensitive   = true
}
