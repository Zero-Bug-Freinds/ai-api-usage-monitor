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
  type = string
}

variable "vpc_cidr" {
  type = string
}

variable "public_subnet_cidrs" {
  type = list(string)
}
