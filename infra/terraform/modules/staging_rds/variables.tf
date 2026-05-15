variable "project_name" {
  type        = string
  description = "Prefix for RDS identifier and subnet group (hyphens normalized in resources)."
}

variable "vpc_id" {
  type        = string
  description = "VPC where the compute stack runs."
}

variable "subnet_ids" {
  type        = list(string)
  description = "At least two subnet IDs (different AZs) for the RDS subnet group."

  validation {
    condition     = length(var.subnet_ids) >= 2
    error_message = "subnet_ids must include at least two subnets in different AZs for RDS."
  }
}

variable "ec2_security_group_id" {
  type        = string
  description = "Security group attached to ASG instances; RDS allows 5432 from this SG only."
}

variable "instance_class" {
  type        = string
  description = "RDS instance class (staging cost control)."
  default     = "db.t4g.micro"
}

variable "allocated_storage" {
  type        = number
  description = "Allocated storage in GB."
  default     = 20
}
