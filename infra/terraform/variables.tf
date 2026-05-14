variable "aws_region" {
  type        = string
  description = "Primary AWS region (ECR, IAM, and optional compute)."
  default     = "ap-northeast-2"
}

variable "project_name" {
  type        = string
  description = "Used for tagging and optional resource name prefixes."
  default     = "ai-api-usage-monitor"
}

variable "default_tags" {
  type        = map(string)
  description = "Additional provider default_tags merged into resources."
  default     = {}
}

variable "github_org" {
  type        = string
  description = "GitHub organization or user (OIDC trust StringLike repo:ORG/REPO:*)."
  default     = "Zero-Bug-Freinds"
}

variable "github_repo" {
  type        = string
  description = "Repository name without org (OIDC trust)."
  default     = "ai-api-usage-monitor"
}

variable "ecr_repository_prefix" {
  type        = string
  description = "ECR repository prefix; must match GitHub Environment variable ECR_REPOSITORY_PREFIX / release.yml default."
  default     = "ai-api-usage-monitor"
}

variable "ecr_repository_suffixes" {
  type        = list(string)
  description = "ECR repository suffixes; each repository name is \"<ecr_repository_prefix>/<suffix>\"."
  default     = ["service-a", "service-b", "service-c"]
}

variable "ecr_untagged_image_expire_days" {
  type        = number
  description = "ECR lifecycle: expire untagged images after this many days (each repository)."
  default     = 14

  validation {
    condition     = var.ecr_untagged_image_expire_days >= 1 && var.ecr_untagged_image_expire_days <= 365
    error_message = "ecr_untagged_image_expire_days must be between 1 and 365."
  }
}

variable "release_iam_role_name" {
  type        = string
  description = "IAM role name for GitHub Actions ECR push (OIDC)."
  default     = "ReleaseRole"
}

variable "deploy_iam_role_name" {
  type        = string
  description = "IAM role name for GitHub Actions deploy (SSM + ELB via OIDC)."
  default     = "DeployRole"
}

variable "enable_compute_stack" {
  type        = bool
  description = "When true, creates VPC (simple public layout), ALB, target group, ASG, launch template, and EC2 instance profile for ECR pull + SSM."
  default     = false
}

variable "compute_environment_label" {
  type        = string
  description = "Tag value for optional compute resources (use staging vs production naming per stack)."
  default     = "staging"
}

variable "compute_instance_type" {
  type        = string
  description = "EC2 instance type for the optional ASG."
  default     = "t3.medium"
}

variable "compute_asg_min_size" {
  type        = number
  description = "ASG minimum size for optional compute stack."
  default     = 1
}

variable "compute_asg_max_size" {
  type        = number
  description = "ASG maximum size for optional compute stack."
  default     = 3
}

variable "compute_asg_desired_capacity" {
  type        = number
  description = "ASG desired capacity for optional compute stack."
  default     = 2
}

variable "alb_target_port" {
  type        = number
  description = "Target group port for instance registration; must match scripts/deploy/gha-roll-instance.sh TARGET_PORT (default 80) and deploy.yml."
  default     = 80
}

variable "alb_health_check_path" {
  type        = string
  description = "ALB target group health check path (see docs/aws-github-oidc-ecr-ssm.md)."
  default     = "/healthz"
}

variable "vpc_cidr" {
  type        = string
  description = "CIDR for optional VPC."
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  type        = list(string)
  description = "Public subnet CIDRs for optional VPC (one or two blocks; module uses at most two AZs)."
  default     = ["10.0.1.0/24", "10.0.2.0/24"]

  validation {
    condition     = length(var.public_subnet_cidrs) >= 1 && length(var.public_subnet_cidrs) <= 2
    error_message = "public_subnet_cidrs must have 1 or 2 entries (compute_stack uses at most two AZs)."
  }

  validation {
    condition     = alltrue([for c in var.public_subnet_cidrs : can(cidrhost(c, 0))])
    error_message = "Each public_subnet_cidrs entry must be a valid IPv4 CIDR block."
  }
}
