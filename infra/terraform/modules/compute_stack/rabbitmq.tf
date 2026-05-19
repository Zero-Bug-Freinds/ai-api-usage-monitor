resource "random_password" "ec2_rabbitmq" {
  count   = var.enable_ec2_rabbitmq ? 1 : 0
  length  = 24
  special = false
}

locals {
  ec2_rabbitmq_password         = var.enable_ec2_rabbitmq ? random_password.ec2_rabbitmq[0].result : ""
  ec2_rabbitmq_password_escaped = replace(local.ec2_rabbitmq_password, "'", "'\\''")
  ec2_rabbitmq_notification_url = var.enable_ec2_rabbitmq ? "amqp://${var.ec2_rabbitmq_user}:${local.ec2_rabbitmq_password}@host.docker.internal:5672/%2f" : ""
}
