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
  description = "Instance target port; align with gha-roll-instance.sh TARGET_PORT (default 80)."
}

variable "health_check_path" {
  type        = string
  description = "HTTP path for the target group health check (e.g. web-edge /healthz)."
}

variable "health_check_port" {
  type        = string
  description = "Health check port: \"traffic-port\" (same as target_port) or a numeric port string. web-edge exposes /healthz on 8080 without Host filtering; traffic often stays on 80."
  default     = "8080"

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
