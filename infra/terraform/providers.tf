provider "aws" {
  region = var.aws_region

  default_tags {
    tags = merge(
      {
        Project = var.project_name
      },
      var.default_tags,
    )
  }
}
