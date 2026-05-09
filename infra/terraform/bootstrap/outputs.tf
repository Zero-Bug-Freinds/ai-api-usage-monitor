output "state_bucket_name" {
  value       = aws_s3_bucket.terraform_state.id
  description = "Pass as backend bucket= in the root module backend configuration."
}

output "state_lock_table_name" {
  value       = aws_dynamodb_table.terraform_locks.name
  description = "Pass as backend dynamodb_table= in the root module backend configuration."
}
