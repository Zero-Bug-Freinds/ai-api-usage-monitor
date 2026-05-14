variable "aws_region" {
  type        = string
  description = "Region for the state bucket and lock table."
}

variable "bucket_name" {
  type        = string
  description = "Globally unique S3 bucket name for Terraform state."

  validation {
    condition     = length(var.bucket_name) >= 3 && length(var.bucket_name) <= 63
    error_message = "bucket_name must be between 3 and 63 characters."
  }
}

variable "dynamodb_table_name" {
  type        = string
  description = "DynamoDB table name used for state locking."

  validation {
    condition     = length(var.dynamodb_table_name) >= 3 && length(var.dynamodb_table_name) <= 255
    error_message = "dynamodb_table_name must be between 3 and 255 characters."
  }
}

variable "kms_key_arn" {
  type        = string
  description = "Optional CMK ARN for SSE-KMS on the state bucket; leave empty for SSE-S3."
  default     = ""
}
