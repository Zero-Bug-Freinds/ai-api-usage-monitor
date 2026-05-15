output "address" {
  description = "RDS hostname for .env.deploy *_POSTGRES_HOST and NOTIFICATION_DATABASE_URL host part."
  value       = aws_db_instance.this.address
}

output "port" {
  value = aws_db_instance.this.port
}

output "master_username" {
  value = aws_db_instance.this.username
}

output "master_password" {
  description = "Store in SSM or password manager; use for initial psql from EC2 to create logical DBs."
  value       = random_password.master.result
  sensitive   = true
}
