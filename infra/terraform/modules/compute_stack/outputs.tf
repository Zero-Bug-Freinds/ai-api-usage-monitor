output "vpc_id" {
  value       = aws_vpc.this.id
  description = "Created VPC ID."
}

output "alb_dns_name" {
  value       = aws_lb.this.dns_name
  description = "ALB DNS name (HTTP 80 listener)."
}

output "alb_target_group_arn" {
  value       = aws_lb_target_group.app.arn
  description = "Target group ARN for GitHub Environment variable ALB_TARGET_GROUP_ARN and deploy IAM scoping."
}

output "alb_target_port" {
  value       = var.target_port
  description = "Target port registered with the ALB; set GitHub Environment TARGET_PORT (or rely on workflow default 8888 when unset)."
}

output "alb_health_check_port" {
  value       = var.health_check_port
  description = "Health check port configured on the target group (see alb_health_check_port root variable)."
}

output "ec2_instance_profile_name" {
  value       = aws_iam_instance_profile.ec2_instance.name
  description = "Attach to the launch template / instances (ECR pull + SSM)."
}

output "public_subnet_ids" {
  value       = aws_subnet.public[*].id
  description = "Public subnets used by ALB and ASG."
}

output "instance_security_group_id" {
  value       = aws_security_group.instance.id
  description = "ASG instance security group (allow RDS ingress from this SG)."
}

output "asg_name" {
  value       = aws_autoscaling_group.app.name
  description = "Auto Scaling group name (alpha stop/start scripts)."
}

output "ec2_rabbitmq_enabled" {
  value       = var.enable_ec2_rabbitmq
  description = "True when user-data installs RabbitMQ on the host via Docker."
}

output "ec2_rabbitmq_user" {
  value       = var.enable_ec2_rabbitmq ? var.ec2_rabbitmq_user : null
  description = "Broker username written to terraform-rabbitmq.env on the instance."
}

output "ec2_rabbitmq_password" {
  value       = var.enable_ec2_rabbitmq ? random_password.ec2_rabbitmq[0].result : null
  description = "Sensitive. Broker password; also in terraform output ec2_rabbitmq_deploy_env."
  sensitive   = true
}

output "ec2_rabbitmq_deploy_env" {
  value = var.enable_ec2_rabbitmq ? {
    RABBITMQ_HOST             = "host.docker.internal"
    RABBITMQ_PORT             = "5672"
    RABBITMQ_USER             = var.ec2_rabbitmq_user
    RABBITMQ_PASSWORD         = random_password.ec2_rabbitmq[0].result
    RABBITMQ_SSL_ENABLED      = "false"
    NOTIFICATION_RABBITMQ_URL = local.ec2_rabbitmq_notification_url
  } : null
  description = "Sensitive map for .env.deploy RABBITMQ_* (host uses host.docker.internal from app containers)."
  sensitive   = true
}
