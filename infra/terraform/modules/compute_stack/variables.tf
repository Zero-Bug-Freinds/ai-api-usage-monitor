variable "project_name" {
  type = string
}

variable "environment_label" {
  type        = string
  description = "Tag value for Environment on compute resources (e.g. staging)."
  default     = "staging"
}

variable "ecr_repository_prefix" {
  type = string
}

variable "instance_type" {
  type = string
}

variable "asg_min_size" {
  type = number
}

variable "asg_max_size" {
  type = number
}

variable "asg_desired_capacity" {
  type = number
}

variable "target_port" {
  type        = number
  description = "Instance target port (web-edge host bind, e.g. 8888); align with gha-roll-instance.sh TARGET_PORT."
}

variable "health_check_path" {
  type        = string
  description = "HTTP path for the target group health check (web-edge nginx: /healthz on :80 and :8080)."
  default     = "/healthz"
}

variable "health_check_port" {
  type        = string
  description = "Health check port: \"traffic-port\" (same as target_port; use with /healthz on web-edge :80) or e.g. \"8080\" for the dedicated web-edge health listener only."
  default     = "traffic-port"

  validation {
    condition = (
      var.health_check_port == "traffic-port"
      || (
        try(tonumber(var.health_check_port), 0) >= 1
        && try(tonumber(var.health_check_port), 0) <= 65535
      )
    )
    error_message = "health_check_port must be \"traffic-port\" or a decimal TCP port (1-65535) as a string."
  }
}

variable "vpc_cidr" {
  type = string
}

variable "public_subnet_cidrs" {
  type = list(string)
}

variable "bootstrap_git_clone_enabled" {
  type        = bool
  description = "When true, user-data clones bootstrap_git_clone_url into /opt/<project_name> if deploy scripts are missing."
  default     = true
}

variable "bootstrap_git_clone_url" {
  type        = string
  description = "HTTPS git URL for optional bootstrap clone (ignored when bootstrap_git_clone_enabled is false)."
  default     = ""
}

variable "bootstrap_image_tag" {
  type        = string
  description = "ECR tag for web-edge-only bootstrap on first boot (must exist in ECR; e.g. staging or a recent release sha)."
  default     = "staging"
}

variable "health_check_grace_period" {
  type        = number
  description = "ASG seconds to ignore ELB health after launch (allow SSM rolling deploy to finish)."
  default     = 900

  validation {
    condition     = var.health_check_grace_period >= 300 && var.health_check_grace_period <= 7200
    error_message = "health_check_grace_period must be between 300 and 7200 seconds."
  }
}
