output "broker_id" {
  description = "Amazon MQ broker id."
  value       = aws_mq_broker.this.id
}

output "broker_arn" {
  value = aws_mq_broker.this.arn
}

output "security_group_id" {
  value = aws_security_group.mq.id
}

output "amqp_endpoint" {
  description = "Primary AMQP(S) endpoint string from AWS (amqps://host:5671)."
  value       = local.primary_amqp_endpoint
}

output "amqp_hostname" {
  description = "Hostname for .env.deploy RABBITMQ_HOST (no port)."
  value       = local.amqp_hostname
}

output "amqp_port" {
  description = "AMQPS port for Amazon MQ RabbitMQ."
  value       = local.amqp_port
}

output "username" {
  value = var.username
}

output "password" {
  value     = random_password.broker.result
  sensitive = true
}

output "ssl_enabled" {
  description = "Use RABBITMQ_SSL_ENABLED=true / SPRING_RABBITMQ_SSL_ENABLED=true with Amazon MQ."
  value       = true
}

output "console_url" {
  description = "RabbitMQ management console URL (HTTPS); reachable from EC2 in the VPC."
  value       = try(aws_mq_broker.this.instances[0].console_url, null)
}

output "notification_rabbitmq_url" {
  description = "Full URL for NOTIFICATION_RABBITMQ_URL (amqps, vhost /)."
  value       = "${local.amqp_scheme}://${var.username}:${random_password.broker.result}@${local.amqp_hostname}:${local.amqp_port}/%2f"
  sensitive   = true
}

output "deploy_env" {
  description = "Key/value map for .env.deploy RABBITMQ_* and NOTIFICATION_RABBITMQ_URL."
  value = {
    RABBITMQ_HOST             = local.amqp_hostname
    RABBITMQ_PORT             = tostring(local.amqp_port)
    RABBITMQ_USER             = var.username
    RABBITMQ_PASSWORD         = random_password.broker.result
    RABBITMQ_SSL_ENABLED      = "true"
    NOTIFICATION_RABBITMQ_URL = "${local.amqp_scheme}://${var.username}:${random_password.broker.result}@${local.amqp_hostname}:${local.amqp_port}/%2f"
  }
  sensitive = true
}
