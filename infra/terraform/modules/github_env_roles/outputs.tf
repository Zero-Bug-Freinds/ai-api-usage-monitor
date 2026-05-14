output "release_role_arn" {
  value       = aws_iam_role.release.arn
  description = "Set as GitHub Environment variable AWS_RELEASE_ROLE_ARN for this environment."
}

output "deploy_role_arn" {
  value       = aws_iam_role.deploy.arn
  description = "Set as GitHub Environment variable AWS_DEPLOY_ROLE_ARN for this environment."
}
