output "repository_arns" {
  description = "Map of suffix -> repository ARN."
  value       = { for k, v in aws_ecr_repository.this : k => v.arn }
}

output "repository_names" {
  description = "Map of suffix -> full repository name (prefix/suffix)."
  value       = { for k, v in aws_ecr_repository.this : k => v.name }
}
