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
  description = "ECR repository suffixes; each repository name is \"<ecr_repository_prefix>/<suffix>\". Must align with .github/workflows/release.yml image names."
  default = [
    "agent-service",
    "agent-web",
    "api-gateway-service",
    "billing-service",
    "billing-web",
    "identity-service",
    "identity-web",
    "notification-service",
    "notification-web",
    "proxy-service",
    "team-service",
    "team-web",
    "usage-service",
    "usage-web",
    "web-edge",
  ]
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
  default     = true
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
  description = "ASG maximum size. Use 1 to allow at most one EC2 (no scale-out above desired)."
  default     = 1
}

variable "compute_asg_desired_capacity" {
  type        = number
  description = "ASG desired capacity for optional compute stack."
  default     = 1
}

variable "alb_target_port" {
  type        = number
  description = "Target group port for instance registration (web-edge host bind); must match scripts/deploy/gha-roll-instance.sh TARGET_PORT and GitHub Environment TARGET_PORT (terraform output alb_target_port)."
  default     = 8888
}

variable "alb_health_check_path" {
  type        = string
  description = "ALB target group health check path (see docs/aws-github-oidc-ecr-ssm.md)."
  default     = "/healthz"
}

variable "alb_health_check_port" {
  type        = string
  description = "Target group health check port: \"traffic-port\" (same as alb_target_port; use with web-edge /healthz on the main listener) or \"8080\" for web-edge dedicated health listener. When not traffic-port, a matching instance SG rule from the ALB SG is created automatically."
  default     = "traffic-port"

  validation {
    condition = (
      var.alb_health_check_port == "traffic-port"
      || (
        try(tonumber(var.alb_health_check_port), 0) >= 1
        && try(tonumber(var.alb_health_check_port), 0) <= 65535
      )
    )
    error_message = "alb_health_check_port must be \"traffic-port\" or a TCP port number as a string (1-65535)."
  }
}

variable "ec2_bootstrap_git_clone_enabled" {
  type        = bool
  description = "When true, EC2 user-data clones the GitHub repo (HTTPS) into /opt/<project_name> if deploy scripts are missing. Set false for air-gapped hosts or private repos without an anonymous clone URL; clone manually or use another sync path."
  default     = true
}

variable "ec2_bootstrap_git_clone_url" {
  type        = string
  description = "Git clone URL for EC2 bootstrap (HTTPS). Leave empty to use https://github.com/<github_org>/<github_repo>.git from root variables."
  default     = ""
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

variable "enable_staging_rds" {
  type        = bool
  description = "When true (and enable_compute_stack), creates one small PostgreSQL RDS in the compute VPC for staging-style logical DBs. Not production MSA physical separation; see docs/msa-database-and-service-integration.md."
  default     = false
}

variable "staging_rds_instance_class" {
  type        = string
  description = "RDS instance class for staging_rds module."
  default     = "db.t4g.micro"
}

variable "staging_rds_allocated_storage" {
  type        = number
  description = "Initial allocated storage (GB) for staging RDS."
  default     = 20
}
