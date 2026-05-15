output "address" {
  description = "RDS hostname for .env.deploy *_POSTGRES_HOST and NOTIFICATION_DATABASE_URL host part."
  value       = aws_db_instance.this.address
}

output "port" {
  value = aws_db_instance.this.port
}

output "endpoint" {
  description = "hostname:port for psql and JDBC."
  value       = aws_db_instance.this.endpoint
}

output "identifier" {
  description = "RDS instance identifier (AWS console / CLI)."
  value       = aws_db_instance.this.identifier
}

output "resource_id" {
  value = aws_db_instance.this.resource_id
}

output "security_group_id" {
  description = "RDS security group (ingress 5432 from EC2 instance SG only)."
  value       = aws_security_group.rds.id
}

output "master_username" {
  value = aws_db_instance.this.username
}

output "master_password" {
  description = "Store in SSM or password manager; use for initial psql from EC2 to create logical DBs."
  value       = random_password.master.result
  sensitive   = true
}
