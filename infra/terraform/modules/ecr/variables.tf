variable "repository_prefix" {
  type        = string
  description = "ECR repository name prefix (first path segment before service suffix)."
}

variable "repository_suffixes" {
  type        = list(string)
  description = "Suffix names matching release.yml image components (prefixed as PREFIX/SUFFIX)."
}
