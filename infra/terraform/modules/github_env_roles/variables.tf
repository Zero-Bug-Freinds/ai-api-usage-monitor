variable "environment_name" {
  type        = string
  description = "GitHub Environment name (staging or production); encoded in OIDC sub claim."

  validation {
    condition     = contains(["staging", "production"], var.environment_name)
    error_message = "environment_name must be staging or production."
  }
}

variable "github_org" {
  type = string
}

variable "github_repo" {
  type = string
}

variable "oidc_provider_arn" {
  type        = string
  description = "IAM OIDC provider ARN for token.actions.githubusercontent.com."
}

variable "iam_role_name_prefix" {
  type = string
}

variable "ecr_repository_prefix" {
  type        = string
  description = "Used to scope ECR push/pull ARNs (repository/PREFIX/*)."
}

variable "deploy_elb_target_group_arns" {
  type        = list(string)
  description = "When non-empty, elasticloadbalancing:RegisterTargets/DeregisterTargets are scoped to these ARNs; DescribeTargetHealth stays Resource *."
  default     = []
}
