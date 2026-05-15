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
  description = "Target group / registration port; set GitHub Environment TARGET_PORT to this value (or leave unset — workflows default to 8888 when var is empty)."
  value       = var.enable_compute_stack ? module.compute[0].alb_target_port : var.alb_target_port
}

output "ssm_deploy_root_default" {
  description = "Recommended GitHub Environment SSM_DEPLOY_ROOT; matches compute EC2 clone path /opt/<project_name>."
  value       = "/opt/${var.project_name}"
}

output "alb_health_check_port" {
  description = "ALB target group health check port (traffic-port or fixed port); must match web-edge and instance SG."
  value       = var.enable_compute_stack ? module.compute[0].alb_health_check_port : var.alb_health_check_port
}

output "ec2_instance_profile_name" {
  description = "Optional compute stack instance profile (ECR pull + SSM)."
  value       = var.enable_compute_stack ? module.compute[0].ec2_instance_profile_name : null
}

output "staging_rds_enabled" {
  description = "True when enable_compute_stack and enable_staging_rds created the staging RDS module."
  value       = length(module.staging_rds) > 0
}

output "staging_rds_address" {
  description = "Postgres host when staging RDS is enabled; set every *_POSTGRES_HOST in .env.deploy to this value."
  value       = length(module.staging_rds) > 0 ? module.staging_rds[0].address : null
}

output "staging_rds_port" {
  description = "Postgres port (default 5432)."
  value       = length(module.staging_rds) > 0 ? module.staging_rds[0].port : null
}

output "staging_rds_endpoint" {
  description = "hostname:port for psql -h/-p and connectivity checks from EC2."
  value       = length(module.staging_rds) > 0 ? module.staging_rds[0].endpoint : null
}

output "staging_rds_identifier" {
  description = "RDS instance identifier in AWS."
  value       = length(module.staging_rds) > 0 ? module.staging_rds[0].identifier : null
}

output "staging_rds_security_group_id" {
  description = "RDS SG; ingress allows TCP 5432 from compute instance_security_group_id only."
  value       = length(module.staging_rds) > 0 ? module.staging_rds[0].security_group_id : null
}

output "staging_rds_master_username" {
  description = "Master user (staging shortcut: use for all *_POSTGRES_USER after logical DB script)."
  value       = length(module.staging_rds) > 0 ? module.staging_rds[0].master_username : null
}

output "staging_rds_master_password" {
  description = "Sensitive. Staging only — copy to SSM or .env.deploy; create logical DBs from EC2 (scripts/deploy/rds-staging-create-logical-dbs.sh)."
  value       = length(module.staging_rds) > 0 ? module.staging_rds[0].master_password : null
  sensitive   = true
}

output "staging_rds" {
  description = "Staging RDS connection summary (null when enable_staging_rds is false or compute stack is off)."
  value = length(module.staging_rds) > 0 ? {
    address               = module.staging_rds[0].address
    port                  = module.staging_rds[0].port
    endpoint              = module.staging_rds[0].endpoint
    identifier            = module.staging_rds[0].identifier
    master_username       = module.staging_rds[0].master_username
    security_group_id     = module.staging_rds[0].security_group_id
    ec2_security_group_id = module.compute[0].instance_security_group_id
  } : null
}

output "staging_mq_enabled" {
  description = "True when enable_compute_stack and is_alpha_test created the Amazon MQ broker."
  value       = length(module.staging_mq) > 0
}

output "staging_mq_amqp_hostname" {
  description = "Amazon MQ hostname for .env.deploy RABBITMQ_HOST."
  value       = length(module.staging_mq) > 0 ? module.staging_mq[0].amqp_hostname : null
}

output "staging_mq_amqp_port" {
  description = "AMQPS port (5671) for .env.deploy RABBITMQ_PORT."
  value       = length(module.staging_mq) > 0 ? module.staging_mq[0].amqp_port : null
}

output "staging_mq_username" {
  value = length(module.staging_mq) > 0 ? module.staging_mq[0].username : null
}

output "staging_mq_password" {
  description = "Sensitive. Copy to .env.deploy RABBITMQ_PASSWORD."
  value       = length(module.staging_mq) > 0 ? module.staging_mq[0].password : null
  sensitive   = true
}

output "staging_mq_console_url" {
  description = "RabbitMQ management console (from EC2 in VPC)."
  value       = length(module.staging_mq) > 0 ? module.staging_mq[0].console_url : null
}

output "staging_mq_notification_rabbitmq_url" {
  description = "Sensitive. Full NOTIFICATION_RABBITMQ_URL (amqps)."
  value       = length(module.staging_mq) > 0 ? module.staging_mq[0].notification_rabbitmq_url : null
  sensitive   = true
}

output "staging_mq_deploy_env" {
  description = "Sensitive map for .env.deploy: RABBITMQ_* and NOTIFICATION_RABBITMQ_URL (terraform output -json staging_mq_deploy_env)."
  value       = length(module.staging_mq) > 0 ? module.staging_mq[0].deploy_env : null
  sensitive   = true
}

output "staging_mq" {
  description = "Amazon MQ connection summary (null when is_alpha_test is false or compute stack is off)."
  value = length(module.staging_mq) > 0 ? {
    broker_id             = module.staging_mq[0].broker_id
    amqp_hostname         = module.staging_mq[0].amqp_hostname
    amqp_port             = module.staging_mq[0].amqp_port
    amqp_endpoint         = module.staging_mq[0].amqp_endpoint
    username              = module.staging_mq[0].username
    ssl_enabled           = module.staging_mq[0].ssl_enabled
    console_url           = module.staging_mq[0].console_url
    security_group_id     = module.staging_mq[0].security_group_id
    ec2_security_group_id = module.compute[0].instance_security_group_id
  } : null
}
