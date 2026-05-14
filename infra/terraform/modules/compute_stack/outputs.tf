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
  description = "Target port registered with the ALB; set GitHub TARGET_PORT to this value for gha-roll-instance.sh."
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
